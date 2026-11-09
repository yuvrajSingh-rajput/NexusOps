package com.nexusops.workflow.application.dto;

import com.nexusops.workflow.domain.WorkflowVersionStatus;
import java.time.Instant;
import java.util.UUID;

public record WorkflowDefinitionResponseDto(
        UUID id,
        UUID organizationId,
        UUID categoryId,
        String code,
        String name,
        String description,
        boolean active,
        Integer latestVersionNumber,
        WorkflowVersionStatus latestVersionStatus,
        Instant createdAt
) {}

