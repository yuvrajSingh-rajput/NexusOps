package com.nexusops.workflow.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateWorkflowDefinitionDto(
        @NotNull(message = "Category ID is required")
        UUID categoryId,

        @NotBlank(message = "Workflow code is required")
        String code,

        @NotBlank(message = "Workflow name is required")
        String name,

        String description
) {}

