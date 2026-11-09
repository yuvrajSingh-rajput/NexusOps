package com.nexusops.workflow.api;

import com.nexusops.shared.api.ApiResponse;
import com.nexusops.shared.tenant.TenantContextHolder;
import com.nexusops.workflow.application.WorkflowDefinitionService;
import com.nexusops.workflow.application.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowDefinitionController {

    private final WorkflowDefinitionService definitionService;

    public WorkflowDefinitionController(WorkflowDefinitionService definitionService) {
        this.definitionService = definitionService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<WorkflowDefinitionResponseDto>>> getDefinitions() {
        UUID organizationId = TenantContextHolder.getRequiredTenantId();
        List<WorkflowDefinitionResponseDto> definitions = definitionService.getDefinitions(organizationId);
        return ResponseEntity.ok(ApiResponse.success(definitions));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WorkflowDefinitionResponseDto>> getDefinition(@PathVariable UUID id) {
        UUID organizationId = TenantContextHolder.getRequiredTenantId();
        WorkflowDefinitionResponseDto def = definitionService.getDefinition(organizationId, id);
        return ResponseEntity.ok(ApiResponse.success(def));
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<ApiResponse<List<WorkflowVersionSummaryDto>>> getVersions(@PathVariable UUID id) {
        UUID organizationId = TenantContextHolder.getRequiredTenantId();
        List<WorkflowVersionSummaryDto> versions = definitionService.getVersions(organizationId, id);
        return ResponseEntity.ok(ApiResponse.success(versions));
    }

    @GetMapping("/{id}/versions/{versionNumber}")
    public ResponseEntity<ApiResponse<WorkflowVersionResponseDto>> getVersion(
            @PathVariable UUID id,
            @PathVariable int versionNumber) {
        UUID organizationId = TenantContextHolder.getRequiredTenantId();
        WorkflowVersionResponseDto ver = definitionService.getVersion(organizationId, id, versionNumber);
        return ResponseEntity.ok(ApiResponse.success(ver));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('WORKFLOW_MANAGE') or hasRole('ORG_ADMIN') or hasAuthority('ROLE_ORG_ADMIN') or hasAuthority('ORG_ADMIN_MANAGE')")
    public ResponseEntity<ApiResponse<WorkflowDefinitionResponseDto>> createDefinition(
            @Valid @RequestBody CreateWorkflowDefinitionDto dto) {
        UUID organizationId = TenantContextHolder.getRequiredTenantId();
        WorkflowDefinitionResponseDto created = definitionService.createDefinition(organizationId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @PostMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('WORKFLOW_MANAGE') or hasRole('ORG_ADMIN') or hasAuthority('ROLE_ORG_ADMIN') or hasAuthority('ORG_ADMIN_MANAGE')")
    public ResponseEntity<ApiResponse<WorkflowVersionResponseDto>> createDraftVersion(@PathVariable UUID id) {
        UUID organizationId = TenantContextHolder.getRequiredTenantId();
        WorkflowVersionResponseDto created = definitionService.createDraftVersion(organizationId, id);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @PutMapping("/{id}/versions/{versionNumber}")
    @PreAuthorize("hasAuthority('WORKFLOW_MANAGE') or hasRole('ORG_ADMIN') or hasAuthority('ROLE_ORG_ADMIN') or hasAuthority('ORG_ADMIN_MANAGE')")
    public ResponseEntity<ApiResponse<WorkflowVersionResponseDto>> saveDraft(
            @PathVariable UUID id,
            @PathVariable int versionNumber,
            @Valid @RequestBody SaveWorkflowDraftDto dto) {
        UUID organizationId = TenantContextHolder.getRequiredTenantId();
        WorkflowVersionResponseDto saved = definitionService.saveDraft(organizationId, id, versionNumber, dto);
        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    @PostMapping("/{id}/versions/{versionNumber}/validate")
    public ResponseEntity<ApiResponse<WorkflowValidationResultDto>> validateVersion(
            @PathVariable UUID id,
            @PathVariable int versionNumber) {
        UUID organizationId = TenantContextHolder.getRequiredTenantId();
        WorkflowValidationResultDto result = definitionService.validateVersion(organizationId, id, versionNumber);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{id}/versions/{versionNumber}/publish")
    @PreAuthorize("hasAuthority('WORKFLOW_MANAGE') or hasRole('ORG_ADMIN') or hasAuthority('ROLE_ORG_ADMIN') or hasAuthority('ORG_ADMIN_MANAGE')")
    public ResponseEntity<ApiResponse<WorkflowVersionResponseDto>> publishVersion(
            @PathVariable UUID id,
            @PathVariable int versionNumber) {
        UUID organizationId = TenantContextHolder.getRequiredTenantId();
        WorkflowVersionResponseDto published = definitionService.publishVersion(organizationId, id, versionNumber);
        return ResponseEntity.ok(ApiResponse.success(published));
    }
}
