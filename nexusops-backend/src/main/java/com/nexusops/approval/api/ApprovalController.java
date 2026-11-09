package com.nexusops.approval.api;

import com.nexusops.approval.application.ApprovalService;
import com.nexusops.approval.application.dto.ApprovalDecisionDto;
import com.nexusops.approval.application.dto.ApprovalInstanceResponseDto;
import com.nexusops.shared.api.ApiResponse;
import com.nexusops.shared.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<ApprovalInstanceResponseDto>>> getPendingApprovals(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<ApprovalInstanceResponseDto> pending = approvalService.getMyPendingApprovals(
                principal.getOrganizationId(), principal
        );
        return ResponseEntity.ok(ApiResponse.success(pending));
    }

    @GetMapping("/my-pending")
    public ResponseEntity<ApiResponse<List<ApprovalInstanceResponseDto>>> getMyPendingApprovals(
            @AuthenticationPrincipal UserPrincipal principal) {
        return getPendingApprovals(principal);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ApprovalInstanceResponseDto>> getApprovalById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        ApprovalInstanceResponseDto approval = approvalService.getApprovalById(
                principal.getOrganizationId(), id
        );
        return ResponseEntity.ok(ApiResponse.success(approval));
    }

    @GetMapping("/request/{requestId}")
    public ResponseEntity<ApiResponse<List<ApprovalInstanceResponseDto>>> getApprovalsForRequest(
            @PathVariable UUID requestId,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<ApprovalInstanceResponseDto> list = approvalService.getApprovalsForRequest(
                principal.getOrganizationId(), requestId
        );
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @PostMapping("/{id}/action")
    public ResponseEntity<ApiResponse<ApprovalInstanceResponseDto>> decideApproval(
            @PathVariable UUID id,
            @Valid @RequestBody ApprovalDecisionDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        ApprovalInstanceResponseDto result = approvalService.decideApproval(
                principal.getOrganizationId(), id, dto, principal
        );
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{id}/decide")
    public ResponseEntity<ApiResponse<ApprovalInstanceResponseDto>> decideApprovalAlias(
            @PathVariable UUID id,
            @Valid @RequestBody ApprovalDecisionDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return decideApproval(id, dto, principal);
    }
}

