package com.nexusops.request.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRequestCategoryDto(
        @NotBlank(message = "Category code is required")
        @Size(max = 50, message = "Code must be 50 characters or less")
        String code,

        @NotBlank(message = "Category name is required")
        @Size(max = 100, message = "Name must be 100 characters or less")
        String name,

        String description,
        String icon,

        @NotBlank(message = "Payload schema definition is required")
        String payloadSchema,

        boolean requiresWorkflow
) {
}
