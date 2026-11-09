package com.nexusops.task.application.dto;

import java.time.Instant;
import java.util.UUID;

public record TaskCommentResponseDto(
        UUID id,
        UUID authorMembershipId,
        String authorName,
        String content,
        Instant createdAt
) {}

