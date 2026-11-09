package com.nexusops.approval.application.dto;

import com.nexusops.approval.domain.ApprovalStatus;
import com.nexusops.approval.domain.ApproverType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ApprovalInstanceResponseDto(
        UUID id,
        UUID organizationId,
        UUID nodeInstanceId,
        UUID requestId,
        String requestNumber,
        String requestTitle,
        String requesterName,
        String departmentName,
        String categoryName,
        Map<String, Object> requestPayload,
        UUID assignedMembershipId,
        UUID assignedRoleId,
        String approverRoleName,
        ApproverType approverType,
        ApprovalStatus status,
        Instant dueAt,
        Instant createdAt,
        List<ApprovalActionResponseDto> actions
) {}

