package com.nexusops.workflow.api;

import com.nexusops.shared.api.ApiResponse;
import com.nexusops.shared.tenant.TenantContextHolder;
import com.nexusops.workflow.application.WorkflowEngineService;
import com.nexusops.workflow.application.dto.NodeInstanceResponseDto;
import com.nexusops.workflow.application.dto.WorkflowInstanceResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workflow-instances")
public class WorkflowInstanceController {

    private final WorkflowEngineService engineService;

    public WorkflowInstanceController(WorkflowEngineService engineService) {
        this.engineService = engineService;
    }

    @GetMapping("/request/{requestId}")
    public ResponseEntity<ApiResponse<WorkflowInstanceResponseDto>> getWorkflowForRequest(@PathVariable UUID requestId) {
        UUID organizationId = TenantContextHolder.getRequiredTenantId();
        return engineService.getWorkflowInstanceForRequest(organizationId, requestId)
                .map(inst -> ResponseEntity.ok(ApiResponse.success(inst)))
                .orElseGet(() -> ResponseEntity.status(404).body(null));
    }

    @GetMapping("/{id}/nodes")
    public ResponseEntity<ApiResponse<List<NodeInstanceResponseDto>>> getNodeInstances(@PathVariable UUID id) {
        UUID organizationId = TenantContextHolder.getRequiredTenantId();
        List<NodeInstanceResponseDto> nodes = engineService.getNodeInstances(organizationId, id);
        return ResponseEntity.ok(ApiResponse.success(nodes));
    }

    @PostMapping("/{id}/signal")
    @PreAuthorize("hasAuthority('APPROVAL_EXECUTE') or hasAuthority('TASK_OPERATE') or hasAuthority('ORG_ADMIN_MANAGE')")
    public ResponseEntity<ApiResponse<WorkflowInstanceResponseDto>> deliverSignal(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> signalPayload) {
        UUID organizationId = TenantContextHolder.getRequiredTenantId();
        String signal = String.valueOf(signalPayload.getOrDefault("signal", "APPROVED"));
        @SuppressWarnings("unchecked")
        Map<String, Object> signalData = (Map<String, Object>) signalPayload.getOrDefault("data", Map.of());

        WorkflowInstanceResponseDto resumed = engineService.resumeWorkflowWithSignal(organizationId, id, signal, signalData);
        return ResponseEntity.ok(ApiResponse.success(resumed));
    }
}
