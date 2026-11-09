package com.nexusops.approval.application;

import com.nexusops.approval.application.dto.*;
import com.nexusops.approval.domain.*;
import com.nexusops.approval.infrastructure.ApprovalActionRepository;
import com.nexusops.approval.infrastructure.ApprovalInstanceRepository;
import com.nexusops.approval.infrastructure.ScheduledJobRepository;
import com.nexusops.identity.application.IdentityService;
import com.nexusops.request.application.RequestService;
import com.nexusops.request.application.dto.RequestResponseDto;
import com.nexusops.shared.events.ApprovalRequestedEvent;
import com.nexusops.shared.exception.AccessDeniedException;
import com.nexusops.shared.exception.ConflictException;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.security.UserPrincipal;
import com.nexusops.workflow.application.WorkflowEngineService;
import com.nexusops.workflow.application.dto.WorkflowInstanceResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    private final ApprovalInstanceRepository approvalRepository;
    private final ApprovalActionRepository actionRepository;
    private final ScheduledJobRepository scheduledJobRepository;
    private final ApproverResolutionService approverResolutionService;
    private final IdentityService identityService;
    private final RequestService requestService;
    private final WorkflowEngineService workflowEngineService;

    public ApprovalService(ApprovalInstanceRepository approvalRepository,
                           ApprovalActionRepository actionRepository,
                           ScheduledJobRepository scheduledJobRepository,
                           ApproverResolutionService approverResolutionService,
                           IdentityService identityService,
                           RequestService requestService,
                           WorkflowEngineService workflowEngineService) {
        this.approvalRepository = approvalRepository;
        this.actionRepository = actionRepository;
        this.scheduledJobRepository = scheduledJobRepository;
        this.approverResolutionService = approverResolutionService;
        this.identityService = identityService;
        this.requestService = requestService;
        this.workflowEngineService = workflowEngineService;
    }

    @EventListener
    public void onApprovalRequested(ApprovalRequestedEvent event) {
        log.info("Handling ApprovalRequestedEvent for request {} at node {}", event.requestId(), event.nodeInstanceId());
        createApproval(event.organizationId(), event.nodeInstanceId(), event.requestId(),
                event.nodeConfiguration(), event.timeoutHours());
    }

    @Transactional
    public ApprovalInstanceEntity createApproval(UUID organizationId, UUID nodeInstanceId, UUID requestId,
                                                 Map<String, Object> nodeConfig, int timeoutHours) {
        log.info("Creating approval instance for request {} at node instance {}", requestId, nodeInstanceId);

        RequestResponseDto request = requestService.getRequest(organizationId, requestId);

        ApproverResolutionService.ResolvedApprover resolved = approverResolutionService.resolveApprover(
                organizationId,
                request.requesterMembershipId(),
                request.departmentId(),
                nodeConfig
        );

        Instant dueAt = timeoutHours > 0 ? Instant.now().plus(Duration.ofHours(timeoutHours)) : null;

        ApprovalInstanceEntity entity = new ApprovalInstanceEntity(
                organizationId,
                nodeInstanceId,
                requestId,
                resolved.assignedMembershipId(),
                resolved.assignedRoleId(),
                resolved.approverType(),
                dueAt
        );

        entity = approvalRepository.save(entity);

        if (dueAt != null) {
            ScheduledJobEntity timeoutJob = new ScheduledJobEntity(
                    organizationId,
                    "APPROVAL_TIMEOUT",
                    entity.getId(),
                    dueAt,
                    Map.of("approvalId", entity.getId().toString(), "requestId", requestId.toString())
            );
            scheduledJobRepository.save(timeoutJob);
        }

        return entity;
    }

    @Transactional(readOnly = true)
    public List<ApprovalInstanceResponseDto> getMyPendingApprovals(UUID organizationId, UserPrincipal principal) {
        UUID membershipId = principal.getMembershipId();
        Set<UUID> roleIds = identityService.getRoleIdsForMembership(organizationId, membershipId);

        List<ApprovalInstanceEntity> list;
        if (roleIds.isEmpty()) {
            list = approvalRepository.findPendingForDirectMembership(organizationId, ApprovalStatus.PENDING, membershipId);
        } else {
            list = approvalRepository.findPendingForApprover(organizationId, ApprovalStatus.PENDING, membershipId, roleIds);
        }

        return list.stream().map(a -> toDto(organizationId, a)).toList();
    }

    @Transactional(readOnly = true)
    public ApprovalInstanceResponseDto getApprovalById(UUID organizationId, UUID approvalId) {
        ApprovalInstanceEntity entity = approvalRepository.findByIdAndOrganizationId(approvalId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Approval instance not found: " + approvalId));
        return toDto(organizationId, entity);
    }

    @Transactional(readOnly = true)
    public List<ApprovalInstanceResponseDto> getApprovalsForRequest(UUID organizationId, UUID requestId) {
        return approvalRepository.findAllByOrganizationIdAndRequestId(organizationId, requestId).stream()
                .map(a -> toDto(organizationId, a))
                .toList();
    }

    @Transactional
    public ApprovalInstanceResponseDto decideApproval(UUID organizationId, UUID approvalId,
                                                      ApprovalDecisionDto dto, UserPrincipal principal) {
        log.info("Processing decision {} for approval {} by membership {}",
                dto.action(), approvalId, principal.getMembershipId());

        ApprovalInstanceEntity instance = approvalRepository.findByIdAndOrganizationId(approvalId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Approval instance not found: " + approvalId));

        if (instance.getStatus() != ApprovalStatus.PENDING) {
            throw new ConflictException("APPROVAL_CONFLICT", "Approval has already been resolved with status: " + instance.getStatus());
        }

        verifyApproverAuthorization(organizationId, instance, principal);

        // 1. Record append-only immutable decision action
        ApprovalActionEntity action = new ApprovalActionEntity(
                organizationId,
                instance.getId(),
                principal.getMembershipId(),
                dto.action(),
                dto.comments()
        );
        actionRepository.save(action);

        // 2. Transition approval instance state
        switch (dto.action()) {
            case APPROVE -> instance.setStatus(ApprovalStatus.APPROVED);
            case REJECT -> instance.setStatus(ApprovalStatus.REJECTED);
            case REQUEST_INFORMATION -> instance.setStatus(ApprovalStatus.REQUESTED_INFO);
            case DELEGATE, REASSIGN -> {
                if (dto.delegateToMembershipId() != null) {
                    instance.setAssignedMembershipId(dto.delegateToMembershipId());
                    instance.setApproverType(ApproverType.DIRECT_MEMBERSHIP);
                }
            }
        }

        // @Version column ensures concurrent decisions trigger OptimisticLockException -> HTTP 409
        instance = approvalRepository.save(instance);

        // 3. Deliver decision signal to Workflow Engine
        Optional<WorkflowInstanceResponseDto> wfOpt = workflowEngineService.getWorkflowInstanceForRequest(
                organizationId, instance.getRequestId()
        );

        if (wfOpt.isPresent()) {
            WorkflowInstanceResponseDto wf = wfOpt.get();
            Map<String, Object> signalData = new HashMap<>();
            signalData.put("actorMembershipId", principal.getMembershipId().toString());
            signalData.put("actorEmail", principal.getEmail());
            signalData.put("comments", dto.comments() != null ? dto.comments() : "");

            if (dto.action() == ApprovalActionType.APPROVE) {
                workflowEngineService.resumeWorkflowWithSignal(organizationId, wf.id(), "APPROVED", signalData);
            } else if (dto.action() == ApprovalActionType.REJECT) {
                workflowEngineService.resumeWorkflowWithSignal(organizationId, wf.id(), "REJECTED", signalData);
                requestService.rejectRequest(organizationId, instance.getRequestId());
            } else if (dto.action() == ApprovalActionType.REQUEST_INFORMATION) {
                requestService.markWaitingForInformation(organizationId, instance.getRequestId());
            }
        }

        return toDto(organizationId, instance);
    }

    private void verifyApproverAuthorization(UUID organizationId, ApprovalInstanceEntity instance, UserPrincipal principal) {
        // Org Admin can override / decide any approval
        if (principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ORG_ADMIN") || a.getAuthority().equals("ORG_ADMIN_MANAGE"))) {
            return;
        }

        UUID callerMembershipId = principal.getMembershipId();

        // 1. Direct Assignment match
        if (instance.getAssignedMembershipId() != null && instance.getAssignedMembershipId().equals(callerMembershipId)) {
            return;
        }

        // 2. Role Queue match
        if (instance.getAssignedRoleId() != null) {
            Set<UUID> callerRoles = identityService.getRoleIdsForMembership(organizationId, callerMembershipId);
            if (callerRoles.contains(instance.getAssignedRoleId())) {
                return;
            }
        }

        throw new AccessDeniedException("You are not authorized to decide this approval.");
    }

    private ApprovalInstanceResponseDto toDto(UUID organizationId, ApprovalInstanceEntity entity) {
        RequestResponseDto req = null;
        try {
            req = requestService.getRequest(organizationId, entity.getRequestId());
        } catch (Exception ignored) {}

        List<ApprovalActionResponseDto> actions = actionRepository.findAllByApprovalInstanceIdOrderByCreatedAtAsc(entity.getId())
                .stream()
                .map(act -> new ApprovalActionResponseDto(
                        act.getId(),
                        act.getActorMembershipId(),
                        identityService.getMemberName(organizationId, act.getActorMembershipId()),
                        act.getAction(),
                        act.getComments(),
                        act.getCreatedAt()
                )).toList();

        return new ApprovalInstanceResponseDto(
                entity.getId(),
                entity.getOrganizationId(),
                entity.getNodeInstanceId(),
                entity.getRequestId(),
                req != null ? req.requestNumber() : "REQ-UNKNOWN",
                req != null ? req.title() : "Unknown Request",
                req != null ? req.requesterName() : "Unknown Requester",
                req != null ? req.departmentName() : "",
                req != null ? req.categoryName() : "",
                req != null ? req.payload() : Map.of(),
                entity.getAssignedMembershipId(),
                entity.getAssignedRoleId(),
                entity.getAssignedRoleId() != null ? "Role Queue" : "Direct Assignee",
                entity.getApproverType(),
                entity.getStatus(),
                entity.getDueAt(),
                entity.getCreatedAt(),
                actions
        );
    }
}
