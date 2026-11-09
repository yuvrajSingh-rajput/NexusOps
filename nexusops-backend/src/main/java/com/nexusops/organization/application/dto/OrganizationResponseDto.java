package com.nexusops.organization.application.dto;

import java.time.Instant;
import java.util.UUID;

public record OrganizationResponseDto(
        UUID id,
        String name,
        String slug,
        String status,
        Instant createdAt
) {
}

