package com.nexusops.notification.application.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponseDto(
        UUID id,
        UUID organizationId,
        UUID recipientMembershipId,
        String eventType,
        String title,
        String message,
        String linkUrl,
        boolean read,
        Instant createdAt
) {
}

