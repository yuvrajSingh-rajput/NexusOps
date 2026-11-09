package com.nexusops.workflow.application.dto;

import com.nexusops.workflow.domain.WorkflowVersionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkflowVersionResponseDto(
        UUID id,
        UUID workflowDefinitionId,
        int versionNumber,
        WorkflowVersionStatus status,
        Instant publishedAt,
        List<WorkflowNodeDto> nodes,
        List<WorkflowTransitionDto> transitions,
        Instant createdAt
) {}
