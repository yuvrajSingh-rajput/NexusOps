package com.nexusops.request.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.organization.application.DepartmentService;
import com.nexusops.organization.application.dto.DepartmentResponseDto;
import com.nexusops.request.application.dto.*;
import com.nexusops.request.domain.*;
import com.nexusops.request.infrastructure.RequestCategoryRepository;
import com.nexusops.request.infrastructure.RequestCommentRepository;
import com.nexusops.request.infrastructure.RequestRepository;
import com.nexusops.request.infrastructure.TenantSequenceRepository;
import com.nexusops.shared.api.PageResponse;
import com.nexusops.shared.exception.AccessDeniedException;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import com.nexusops.shared.security.UserPrincipal;
import com.nexusops.shared.tenant.TenantContextHolder;
import com.nexusops.workflow.application.WorkflowEngineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class RequestService {

    private static final Logger log = LoggerFactory.getLogger(RequestService.class);

    private final RequestRepository requestRepository;
    private final RequestCategoryRepository categoryRepository;
    private final RequestCommentRepository commentRepository;
    private final TenantSequenceRepository sequenceRepository;
    private final RequestCategoryService categoryService;
    private final DepartmentService departmentService;
    private final WorkflowEngineService workflowEngineService;
    private final ObjectMapper objectMapper;

    public RequestService(RequestRepository requestRepository,
                          RequestCategoryRepository categoryRepository,
                          RequestCommentRepository commentRepository,
                          TenantSequenceRepository sequenceRepository,
                          RequestCategoryService categoryService,
                          DepartmentService departmentService,
                          WorkflowEngineService workflowEngineService,
                          ObjectMapper objectMapper) {
        this.requestRepository = requestRepository;
        this.categoryRepository = categoryRepository;
        this.commentRepository = commentRepository;
        this.sequenceRepository = sequenceRepository;
        this.categoryService = categoryService;
        this.departmentService = departmentService;
        this.workflowEngineService = workflowEngineService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RequestResponseDto createRequest(CreateRequestDto dto, UserPrincipal principal) {
        UUID orgId = TenantContextHolder.requireTenantId();
        UUID requesterMembershipId = principal.getMembershipId();

        // 1. Verify Category exists in active tenant
        RequestCategoryEntity category = categoryRepository.findByOrganizationIdAndId(orgId, dto.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Request category not found with id: " + dto.categoryId()));

        if (!category.isActive()) {
            throw new ValidationException("Selected request category is inactive.");
        }

        // 2. Determine department ID
        UUID departmentId = dto.departmentId();
        if (departmentId == null) {
            // Pick default department from active tenant
            List<DepartmentResponseDto> depts = departmentService.getDepartments();
            if (depts.isEmpty()) {
                throw new ValidationException("No departments established in organization. Please create a department first.");
            }
            departmentId = depts.get(0).id();
        } else {
            departmentService.getDepartmentById(departmentId);
        }

        // 3. Validate dynamic payload against category's JSON Schema
        categoryService.validatePayloadAgainstSchema(category.getPayloadSchema(), dto.payload());

        // 4. Generate next atomic human-readable request number (e.g. REQ-1001)
        String requestNumber = generateNextRequestNumber(orgId);

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(dto.payload());
        } catch (JsonProcessingException e) {
            throw new ValidationException("Invalid payload structure.");
        }

        // 5. Freeze snapshot of category JSON schema
        String schemaSnapshot = category.getPayloadSchema();

        RequestEntity entity = new RequestEntity(
                orgId,
                requestNumber,
                category.getId(),
                requesterMembershipId,
                departmentId,
                dto.title().trim(),
                dto.description(),
                dto.priority(),
                payloadJson,
                schemaSnapshot
        );

        if (dto.autoSubmit()) {
            entity.submit();
        }

        RequestEntity saved = requestRepository.save(entity);

        if (dto.autoSubmit() && category.isRequiresWorkflow()) {
            try {
                workflowEngineService.startWorkflow(orgId, saved.getId(), category.getId(), dto.payload());
                saved.setStatus(RequestStatus.IN_REVIEW);
                saved = requestRepository.save(saved);
            } catch (Exception e) {
                log.warn("Workflow not started for request {}: {}", saved.getId(), e.getMessage());
            }
        }

        return toDto(saved, category.getCode(), category.getName(), principal.getEmail());
    }

    @Transactional
    public RequestResponseDto submitRequest(UUID requestId, UserPrincipal principal) {
        UUID orgId = TenantContextHolder.requireTenantId();
        RequestEntity entity = requestRepository.findByOrganizationIdAndId(orgId, requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found with id: " + requestId));

        verifyRequestOwnershipOrAdmin(entity, principal);
        entity.submit();
        RequestEntity saved = requestRepository.save(entity);

        RequestCategoryEntity cat = categoryRepository.findByOrganizationIdAndId(orgId, saved.getCategoryId()).orElse(null);
        if (cat != null && cat.isRequiresWorkflow()) {
            try {
                Map<String, Object> payloadMap = parsePayloadJson(saved.getPayload());
                workflowEngineService.startWorkflow(orgId, saved.getId(), cat.getId(), payloadMap);
                saved.setStatus(RequestStatus.IN_REVIEW);
                saved = requestRepository.save(saved);
            } catch (Exception e) {
                log.warn("Workflow not started for submitted request {}: {}", saved.getId(), e.getMessage());
            }
        }

        String catCode = cat != null ? cat.getCode() : "UNKNOWN";
        String catName = cat != null ? cat.getName() : "Unknown Category";
        return toDto(saved, catCode, catName, principal.getEmail());
    }

    @Transactional
    public RequestResponseDto cancelRequest(UUID requestId, String reason, UserPrincipal principal) {
        UUID orgId = TenantContextHolder.requireTenantId();
        RequestEntity entity = requestRepository.findByOrganizationIdAndId(orgId, requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found with id: " + requestId));

        verifyRequestOwnershipOrAdmin(entity, principal);
        entity.cancel();

        if (reason != null && !reason.isBlank()) {
            RequestCommentEntity comment = new RequestCommentEntity(
                    orgId,
                    requestId,
                    principal.getMembershipId(),
                    "Request cancelled: " + reason.trim(),
                    CommentVisibility.REQUESTER_VISIBLE
            );
            commentRepository.save(comment);
        }

        RequestEntity saved = requestRepository.save(entity);
        RequestCategoryEntity cat = categoryRepository.findByOrganizationIdAndId(orgId, saved.getCategoryId()).orElse(null);
        return toDto(saved, cat != null ? cat.getCode() : "", cat != null ? cat.getName() : "", principal.getEmail());
    }

    @Transactional(readOnly = true)
    public PageResponse<RequestResponseDto> getMyRequests(Pageable pageable, UserPrincipal principal) {
        UUID orgId = TenantContextHolder.requireTenantId();
        Page<RequestEntity> page = requestRepository.findAllByOrganizationIdAndRequesterMembershipId(
                orgId, principal.getMembershipId(), pageable);

        return toPageResponse(page, principal.getEmail());
    }

    @Transactional(readOnly = true)
    public PageResponse<RequestResponseDto> getAllRequests(Pageable pageable, UserPrincipal principal) {
        UUID orgId = TenantContextHolder.requireTenantId();
        Page<RequestEntity> page = requestRepository.findAllByOrganizationId(orgId, pageable);
        return toPageResponse(page, principal.getEmail());
    }

    @Transactional(readOnly = true)
    public RequestResponseDto getRequestById(UUID id, UserPrincipal principal) {
        UUID orgId = TenantContextHolder.requireTenantId();
        RequestEntity entity = requestRepository.findByOrganizationIdAndId(orgId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found with id: " + id));

        RequestCategoryEntity cat = categoryRepository.findByOrganizationIdAndId(orgId, entity.getCategoryId()).orElse(null);
        return toDto(entity, cat != null ? cat.getCode() : "", cat != null ? cat.getName() : "", principal.getEmail());
    }

    @Transactional
    public RequestCommentResponseDto addComment(UUID requestId, AddCommentDto dto, UserPrincipal principal) {
        UUID orgId = TenantContextHolder.requireTenantId();
        RequestEntity request = requestRepository.findByOrganizationIdAndId(orgId, requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found with id: " + requestId));

        CommentVisibility visibility = dto.visibility() != null ? dto.visibility() : CommentVisibility.REQUESTER_VISIBLE;

        // Only operators/admins can post internal comments
        if (visibility == CommentVisibility.INTERNAL && !isOperatorOrAdmin(principal)) {
            visibility = CommentVisibility.REQUESTER_VISIBLE;
        }

        RequestCommentEntity comment = new RequestCommentEntity(
                orgId,
                request.getId(),
                principal.getMembershipId(),
                dto.content().trim(),
                visibility
        );

        RequestCommentEntity saved = commentRepository.save(comment);
        return new RequestCommentResponseDto(
                saved.getId(),
                saved.getRequestId(),
                saved.getAuthorMembershipId(),
                principal.getEmail(),
                saved.getContent(),
                saved.getVisibility(),
                saved.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<RequestCommentResponseDto> getComments(UUID requestId, UserPrincipal principal) {
        UUID orgId = TenantContextHolder.requireTenantId();
        requestRepository.findByOrganizationIdAndId(orgId, requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found with id: " + requestId));

        List<RequestCommentEntity> comments;
        if (isOperatorOrAdmin(principal)) {
            comments = commentRepository.findAllByOrganizationIdAndRequestIdOrderByCreatedAtAsc(orgId, requestId);
        } else {
            comments = commentRepository.findAllByOrganizationIdAndRequestIdAndVisibilityOrderByCreatedAtAsc(
                    orgId, requestId, CommentVisibility.REQUESTER_VISIBLE);
        }

        return comments.stream()
                .map(c -> new RequestCommentResponseDto(
                        c.getId(),
                        c.getRequestId(),
                        c.getAuthorMembershipId(),
                        c.getAuthorMembershipId().equals(principal.getMembershipId()) ? principal.getEmail() : "Team Member",
                        c.getContent(),
                        c.getVisibility(),
                        c.getCreatedAt()
                ))
                .toList();
    }

    private synchronized String generateNextRequestNumber(UUID orgId) {
        TenantSequenceId seqId = new TenantSequenceId(orgId, "REQUEST");
        TenantSequenceEntity seq = sequenceRepository.findById(seqId)
                .orElseGet(() -> sequenceRepository.save(new TenantSequenceEntity(seqId, 1000L)));

        Long nextVal = seq.incrementAndGet();
        sequenceRepository.save(seq);
        return "REQ-" + nextVal;
    }

    private void verifyRequestOwnershipOrAdmin(RequestEntity entity, UserPrincipal principal) {
        if (!entity.getRequesterMembershipId().equals(principal.getMembershipId()) && !isOperatorOrAdmin(principal)) {
            throw new AccessDeniedException("You are not authorized to perform actions on this request.");
        }
    }

    private boolean isOperatorOrAdmin(UserPrincipal principal) {
        return principal.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_ORG_ADMIN") ||
                a.getAuthority().equals("ROLE_OPERATOR") ||
                a.getAuthority().equals("ROLE_MANAGER") ||
                a.getAuthority().equals("ROLE_FINANCE_OPERATOR") ||
                a.getAuthority().equals("ROLE_IT_OPERATOR"));
    }

    private PageResponse<RequestResponseDto> toPageResponse(Page<RequestEntity> page, String defaultUserEmail) {
        UUID orgId = TenantContextHolder.requireTenantId();
        Map<UUID, RequestCategoryEntity> catMap = new HashMap<>();
        categoryRepository.findAllByOrganizationId(orgId).forEach(c -> catMap.put(c.getId(), c));

        List<RequestResponseDto> dtoList = page.getContent().stream().map(r -> {
            RequestCategoryEntity cat = catMap.get(r.getCategoryId());
            String catCode = cat != null ? cat.getCode() : "";
            String catName = cat != null ? cat.getName() : "";
            return toDto(r, catCode, catName, defaultUserEmail);
        }).toList();

        return new PageResponse<>(
                dtoList,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

    private Map<String, Object> parsePayloadJson(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private RequestResponseDto toDto(RequestEntity r, String categoryCode, String categoryName, String requesterEmail) {
        Map<String, Object> payloadMap = parsePayloadJson(r.getPayload());

        String deptName = "Default Department";
        try {
            DepartmentResponseDto dept = departmentService.getDepartmentById(r.getDepartmentId());
            deptName = dept.name();
        } catch (Exception ignored) {
        }

        return new RequestResponseDto(
                r.getId(),
                r.getOrganizationId(),
                r.getRequestNumber(),
                r.getCategoryId(),
                categoryCode,
                categoryName,
                r.getRequesterMembershipId(),
                requesterEmail,
                requesterEmail,
                r.getDepartmentId(),
                deptName,
                r.getTitle(),
                r.getDescription(),
                r.getPriority(),
                r.getStatus(),
                payloadMap,
                r.getSchemaSnapshot(),
                r.getSubmittedAt(),
                r.getCompletedAt(),
                r.getVersion(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}

