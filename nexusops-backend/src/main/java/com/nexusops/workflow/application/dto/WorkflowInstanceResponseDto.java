package com.nexusops.workflow.application.dto;

import com.nexusops.workflow.domain.WorkflowStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record WorkflowInstanceResponseDto(
        UUID id,
        UUID organizationId,
        UUID workflowVersionId,
        UUID requestId,
        int attemptNumber,
        WorkflowStatus status,
        String currentNodeKey,
        Map<String, Object> contextData,
        Instant startedAt,
        Instant completedAt
) {}
