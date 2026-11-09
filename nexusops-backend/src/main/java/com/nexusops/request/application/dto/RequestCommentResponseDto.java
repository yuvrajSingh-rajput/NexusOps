package com.nexusops.request.application.dto;

import com.nexusops.request.domain.CommentVisibility;

import java.time.Instant;
import java.util.UUID;

public record RequestCommentResponseDto(
        UUID id,
        UUID requestId,
        UUID authorMembershipId,
        String authorName,
        String content,
        CommentVisibility visibility,
        Instant createdAt
) {
}
