package com.nexusops.request.application.dto;

import com.nexusops.request.domain.RequestPriority;
import com.nexusops.request.domain.RequestStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RequestResponseDto(
        UUID id,
        UUID organizationId,
        String requestNumber,
        UUID categoryId,
        String categoryCode,
        String categoryName,
        UUID requesterMembershipId,
        String requesterName,
        String requesterEmail,
        UUID departmentId,
        String departmentName,
        String title,
        String description,
        RequestPriority priority,
        RequestStatus status,
        Map<String, Object> payload,
        String schemaSnapshot,
        Instant submittedAt,
        Instant completedAt,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
}

