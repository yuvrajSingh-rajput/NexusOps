package com.nexusops.task.application.dto;

import jakarta.validation.constraints.NotBlank;

public record AddTaskCommentDto(
        @NotBlank(message = "Comment content cannot be empty")
        String content
) {}

