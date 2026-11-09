package com.nexusops.workflow.application.dto;

import com.nexusops.workflow.domain.WorkflowVersionStatus;

import java.time.Instant;
import java.util.UUID;

public record WorkflowVersionSummaryDto(
        UUID id,
        int versionNumber,
        WorkflowVersionStatus status,
        Instant publishedAt,
        Instant createdAt
) {}

