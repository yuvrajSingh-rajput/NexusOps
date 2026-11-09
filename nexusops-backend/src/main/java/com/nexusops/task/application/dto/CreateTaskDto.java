package com.nexusops.task.application.dto;

import com.nexusops.task.domain.TaskPriority;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;
import java.util.UUID;

public record CreateTaskDto(
        @NotBlank(message = "Task title is required")
        String title,
        String description,
        UUID requestId,
        UUID teamId,
        UUID assigneeMembershipId,
        TaskPriority priority,
        Map<String, Object> payload,
        Integer dueInHours
) {}

