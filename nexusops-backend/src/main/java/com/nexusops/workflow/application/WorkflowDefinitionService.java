package com.nexusops.workflow.application;

import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import com.nexusops.workflow.application.dto.*;
import com.nexusops.workflow.application.validator.WorkflowGraphValidator;
import com.nexusops.workflow.domain.*;
import com.nexusops.workflow.infrastructure.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class WorkflowDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowDefinitionService.class);

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowVersionRepository versionRepository;
    private final WorkflowNodeRepository nodeRepository;
    private final WorkflowTransitionRepository transitionRepository;
    private final WorkflowGraphValidator graphValidator;

    public WorkflowDefinitionService(WorkflowDefinitionRepository definitionRepository,
                                     WorkflowVersionRepository versionRepository,
                                     WorkflowNodeRepository nodeRepository,
                                     WorkflowTransitionRepository transitionRepository,
                                     WorkflowGraphValidator graphValidator) {
        this.definitionRepository = definitionRepository;
        this.versionRepository = versionRepository;
        this.nodeRepository = nodeRepository;
        this.transitionRepository = transitionRepository;
        this.graphValidator = graphValidator;
    }

    @Transactional(readOnly = true)
    public List<WorkflowDefinitionResponseDto> getDefinitions(UUID organizationId) {
        return definitionRepository.findAllByOrganizationId(organizationId).stream()
                .map(this::mapDefinitionToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkflowDefinitionResponseDto getDefinition(UUID organizationId, UUID definitionId) {
        WorkflowDefinitionEntity def = definitionRepository.findByIdAndOrganizationId(definitionId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow definition not found: " + definitionId));
        return mapDefinitionToDto(def);
    }

    @Transactional(readOnly = true)
    public WorkflowVersionResponseDto getVersion(UUID organizationId, UUID definitionId, int versionNumber) {
        WorkflowDefinitionEntity def = definitionRepository.findByIdAndOrganizationId(definitionId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow definition not found: " + definitionId));

        WorkflowVersionEntity ver = versionRepository.findByWorkflowDefinitionIdAndVersionNumber(def.getId(), versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow version " + versionNumber + " not found"));

        List<WorkflowNodeEntity> nodes = nodeRepository.findAllByWorkflowVersionId(ver.getId());
        List<WorkflowTransitionEntity> transitions = transitionRepository.findAllByWorkflowVersionId(ver.getId());

        return mapVersionToDto(ver, nodes, transitions);
    }

    @Transactional
    public WorkflowDefinitionResponseDto createDefinition(UUID organizationId, CreateWorkflowDefinitionDto dto) {
        if (definitionRepository.existsByOrganizationIdAndCode(organizationId, dto.code())) {
            throw new ValidationException("Workflow with code '" + dto.code() + "' already exists in this organization.");
        }

        WorkflowDefinitionEntity def = new WorkflowDefinitionEntity(
                organizationId,
                dto.categoryId(),
                dto.code(),
                dto.name(),
                dto.description()
        );
        def = definitionRepository.save(def);

        // Auto-create initial draft version 1
        WorkflowVersionEntity ver = new WorkflowVersionEntity(def.getId(), 1);
        versionRepository.save(ver);

        return mapDefinitionToDto(def);
    }

    @Transactional
    public WorkflowVersionResponseDto publishVersion(UUID organizationId, UUID definitionId, int versionNumber) {
        WorkflowDefinitionEntity def = definitionRepository.findByIdAndOrganizationId(definitionId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow definition not found: " + definitionId));

        WorkflowVersionEntity ver = versionRepository.findByWorkflowDefinitionIdAndVersionNumber(def.getId(), versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow version " + versionNumber + " not found"));

        if (ver.getStatus() == WorkflowVersionStatus.PUBLISHED) {
            throw new ValidationException("Workflow version " + versionNumber + " is already published and immutable.");
        }

        List<WorkflowNodeEntity> nodes = nodeRepository.findAllByWorkflowVersionId(ver.getId());
        List<WorkflowTransitionEntity> transitions = transitionRepository.findAllByWorkflowVersionId(ver.getId());

        // Strictly validate graph rules
        graphValidator.validateGraph(nodes, transitions);

        // Retire previously published versions for this definition
        versionRepository.findByWorkflowDefinitionIdAndStatus(def.getId(), WorkflowVersionStatus.PUBLISHED)
                .ifPresent(prev -> {
                    prev.setStatus(WorkflowVersionStatus.RETIRED);
                    versionRepository.save(prev);
                });

        ver.setStatus(WorkflowVersionStatus.PUBLISHED);
        ver.setPublishedAt(Instant.now());

        // Snapshot graph compilation
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("nodes", nodes.stream().map(n -> Map.of(
                "key", n.getNodeKey(),
                "type", n.getNodeType().name(),
                "title", n.getTitle(),
                "configuration", n.getConfiguration()
        )).toList());
        ver.setDefinitionSnapshot(snapshot);

        ver = versionRepository.save(ver);
        log.info("Published immutable workflow version {} for definition {}", versionNumber, def.getCode());

        return mapVersionToDto(ver, nodes, transitions);
    }

    @Transactional
    public void seedReferenceLaptopWorkflow(UUID organizationId, UUID categoryId) {
        if (definitionRepository.findByOrganizationIdAndCode(organizationId, "WF_DEVELOPER_LAPTOP").isPresent()) {
            return;
        }

        log.info("Bootstrapping reference Laptop Purchase workflow for organization {}", organizationId);

        WorkflowDefinitionEntity def = new WorkflowDefinitionEntity(
                organizationId,
                categoryId,
                "WF_DEVELOPER_LAPTOP",
                "Developer Laptop Purchase Workflow",
                "Automated multi-tier approval routing for laptop hardware requisitions ($10k / $50k thresholds)"
        );
        def = definitionRepository.save(def);

        WorkflowVersionEntity ver = new WorkflowVersionEntity(def.getId(), 1);
        ver = versionRepository.save(ver);

        // 1. Nodes
        WorkflowNodeEntity nStart = nodeRepository.save(new WorkflowNodeEntity(
                ver.getId(), "START", NodeType.START, "Request Submitted", Map.of()
        ));
        WorkflowNodeEntity nManager = nodeRepository.save(new WorkflowNodeEntity(
                ver.getId(), "MGR_APPROVAL", NodeType.APPROVAL, "Direct Manager Approval",
                Map.of("approverType", "MANAGER", "timeoutHours", 48)
        ));
        WorkflowNodeEntity nCond = nodeRepository.save(new WorkflowNodeEntity(
                ver.getId(), "COND_AMOUNT", NodeType.CONDITION, "Evaluate Purchase Amount", Map.of()
        ));
        WorkflowNodeEntity nFinance = nodeRepository.save(new WorkflowNodeEntity(
                ver.getId(), "FIN_APPROVAL", NodeType.APPROVAL, "Finance Budget Approval",
                Map.of("approverRole", "FINANCE", "timeoutHours", 72)
        ));
        WorkflowNodeEntity nDeptHead = nodeRepository.save(new WorkflowNodeEntity(
                ver.getId(), "HEAD_APPROVAL", NodeType.APPROVAL, "Department Head Approval",
                Map.of("approverType", "DEPT_HEAD", "timeoutHours", 72)
        ));
        WorkflowNodeEntity nTask = nodeRepository.save(new WorkflowNodeEntity(
                ver.getId(), "TASK_PROCUREMENT", NodeType.TASK, "IT Procurement & Provisioning",
                Map.of("taskQueue", "IT_OPERATIONS", "slaHours", 120)
        ));
        WorkflowNodeEntity nEndCompleted = nodeRepository.save(new WorkflowNodeEntity(
                ver.getId(), "END_COMPLETED", NodeType.END, "Request Completed",
                Map.of("outcome", "COMPLETED")
        ));
        WorkflowNodeEntity nEndRejected = nodeRepository.save(new WorkflowNodeEntity(
                ver.getId(), "END_REJECTED", NodeType.END, "Request Rejected",
                Map.of("outcome", "REJECTED")
        ));

        // 2. Transitions
        // START -> MGR_APPROVAL
        transitionRepository.save(new WorkflowTransitionEntity(ver.getId(), nStart.getId(), nManager.getId(), null, 0, true));

        // MGR_APPROVAL -> COND_AMOUNT (on approve)
        transitionRepository.save(new WorkflowTransitionEntity(
                ver.getId(), nManager.getId(), nCond.getId(),
                Map.of("field", "context.signal", "operator", "EQUALS", "value", "APPROVED"),
                1, true
        ));
        // MGR_APPROVAL -> END_REJECTED (on reject)
        transitionRepository.save(new WorkflowTransitionEntity(
                ver.getId(), nManager.getId(), nEndRejected.getId(),
                Map.of("field", "context.signal", "operator", "EQUALS", "value", "REJECTED"),
                2, false
        ));

        // COND_AMOUNT Branches:
        // <= 10000 -> IT Task directly
        transitionRepository.save(new WorkflowTransitionEntity(
                ver.getId(), nCond.getId(), nTask.getId(),
                Map.of("field", "payload.estimatedAmount", "operator", "LESS_THAN_OR_EQUAL", "value", 10000),
                1, false
        ));
        // > 10000 AND <= 50000 -> Finance Approval
        transitionRepository.save(new WorkflowTransitionEntity(
                ver.getId(), nCond.getId(), nFinance.getId(),
                Map.of("field", "payload.estimatedAmount", "operator", "LESS_THAN_OR_EQUAL", "value", 50000),
                2, false
        ));
        // > 50000 -> Dept Head Approval (fallback default route)
        transitionRepository.save(new WorkflowTransitionEntity(
                ver.getId(), nCond.getId(), nDeptHead.getId(), null, 3, true
        ));

        // FIN_APPROVAL Transitions:
        transitionRepository.save(new WorkflowTransitionEntity(
                ver.getId(), nFinance.getId(), nTask.getId(),
                Map.of("field", "context.signal", "operator", "EQUALS", "value", "APPROVED"),
                1, true
        ));
        transitionRepository.save(new WorkflowTransitionEntity(
                ver.getId(), nFinance.getId(), nEndRejected.getId(),
                Map.of("field", "context.signal", "operator", "EQUALS", "value", "REJECTED"),
                2, false
        ));

        // HEAD_APPROVAL Transitions:
        transitionRepository.save(new WorkflowTransitionEntity(
                ver.getId(), nDeptHead.getId(), nTask.getId(),
                Map.of("field", "context.signal", "operator", "EQUALS", "value", "APPROVED"),
                1, true
        ));
        transitionRepository.save(new WorkflowTransitionEntity(
                ver.getId(), nDeptHead.getId(), nEndRejected.getId(),
                Map.of("field", "context.signal", "operator", "EQUALS", "value", "REJECTED"),
                2, false
        ));

        // TASK_PROCUREMENT -> END_COMPLETED
        transitionRepository.save(new WorkflowTransitionEntity(ver.getId(), nTask.getId(), nEndCompleted.getId(), null, 0, true));

        // Validate and Publish
        List<WorkflowNodeEntity> nodes = nodeRepository.findAllByWorkflowVersionId(ver.getId());
        List<WorkflowTransitionEntity> transitions = transitionRepository.findAllByWorkflowVersionId(ver.getId());
        graphValidator.validateGraph(nodes, transitions);

        ver.setStatus(WorkflowVersionStatus.PUBLISHED);
        ver.setPublishedAt(Instant.now());
        versionRepository.save(ver);
        log.info("Successfully published Reference Laptop Workflow v1 for organization {}", organizationId);
    }

    private WorkflowDefinitionResponseDto mapDefinitionToDto(WorkflowDefinitionEntity def) {
        List<WorkflowVersionEntity> versions = versionRepository.findAllByWorkflowDefinitionIdOrderByVersionNumberDesc(def.getId());
        Integer latestNum = versions.isEmpty() ? null : versions.get(0).getVersionNumber();
        WorkflowVersionStatus latestStatus = versions.isEmpty() ? null : versions.get(0).getStatus();

        return new WorkflowDefinitionResponseDto(
                def.getId(),
                def.getOrganizationId(),
                def.getCategoryId(),
                def.getCode(),
                def.getName(),
                def.getDescription(),
                def.isActive(),
                latestNum,
                latestStatus,
                def.getCreatedAt()
        );
    }

    private WorkflowVersionResponseDto mapVersionToDto(WorkflowVersionEntity ver,
                                                       List<WorkflowNodeEntity> nodes,
                                                       List<WorkflowTransitionEntity> transitions) {
        List<WorkflowNodeDto> nodeDtos = nodes.stream()
                .map(n -> new WorkflowNodeDto(
                        n.getId(), n.getNodeKey(), n.getNodeType(), n.getTitle(),
                        n.getConfiguration(), n.getPositionX(), n.getPositionY()
                )).toList();

        List<WorkflowTransitionDto> transitionDtos = transitions.stream()
                .map(t -> new WorkflowTransitionDto(
                        t.getId(), t.getSourceNodeId(), t.getTargetNodeId(),
                        t.getConditionExpression(), t.getPriority(), t.isDefault()
                )).toList();

        return new WorkflowVersionResponseDto(
                ver.getId(),
                ver.getWorkflowDefinitionId(),
                ver.getVersionNumber(),
                ver.getStatus(),
                ver.getPublishedAt(),
                nodeDtos,
                transitionDtos,
                ver.getCreatedAt()
        );
    }
}

