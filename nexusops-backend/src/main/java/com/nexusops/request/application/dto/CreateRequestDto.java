package com.nexusops.request.application.dto;

import com.nexusops.request.domain.RequestPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public record CreateRequestDto(
        @NotNull(message = "Category ID is required")
        UUID categoryId,

        UUID departmentId,

        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        String description,

        RequestPriority priority,

        @NotNull(message = "Payload is required")
        Map<String, Object> payload,

        boolean autoSubmit
) {
}
