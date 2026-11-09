package com.nexusops.task.application.dto;

import com.nexusops.task.domain.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTaskStatusDto(
        @NotNull(message = "Task status is required")
        TaskStatus status,
        String notes
) {}

