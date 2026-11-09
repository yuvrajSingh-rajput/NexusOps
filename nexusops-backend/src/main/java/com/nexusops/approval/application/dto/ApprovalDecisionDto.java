package com.nexusops.approval.application.dto;

import com.nexusops.approval.domain.ApprovalActionType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ApprovalDecisionDto(
        @NotNull(message = "Action type is required")
        ApprovalActionType action,

        String comments,

        UUID delegateToMembershipId
) {}

