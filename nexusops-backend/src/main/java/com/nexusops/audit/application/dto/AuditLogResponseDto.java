package com.nexusops.audit.application.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponseDto(
        UUID id,
        UUID organizationId,
        UUID actorMembershipId,
        String actorName,
        String actorEmail,
        String eventType,
        String aggregateType,
        UUID aggregateId,
        String correlationId,
        String beforeState,
        String afterState,
        Instant createdAt
) {
}
