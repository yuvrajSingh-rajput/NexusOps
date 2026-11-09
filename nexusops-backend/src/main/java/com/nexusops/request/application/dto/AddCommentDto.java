package com.nexusops.request.application.dto;

import com.nexusops.request.domain.CommentVisibility;
import jakarta.validation.constraints.NotBlank;

public record AddCommentDto(
        @NotBlank(message = "Comment content cannot be blank")
        String content,

        CommentVisibility visibility
) {
}

