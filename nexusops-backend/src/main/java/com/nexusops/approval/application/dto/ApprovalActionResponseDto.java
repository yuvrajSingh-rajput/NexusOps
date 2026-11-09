package com.nexusops.approval.application.dto;

import com.nexusops.approval.domain.ApprovalActionType;
import java.time.Instant;
import java.util.UUID;

public record ApprovalActionResponseDto(
        UUID id,
        UUID actorMembershipId,
        String actorName,
        ApprovalActionType action,
        String comments,
        Instant createdAt
) {}

