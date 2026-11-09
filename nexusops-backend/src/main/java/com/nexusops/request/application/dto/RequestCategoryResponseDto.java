package com.nexusops.request.application.dto;

import java.util.UUID;

public record RequestCategoryResponseDto(
        UUID id,
        UUID organizationId,
        String code,
        String name,
        String description,
        String icon,
        String payloadSchema,
        int schemaVersion,
        boolean requiresWorkflow,
        boolean active
) {
}

