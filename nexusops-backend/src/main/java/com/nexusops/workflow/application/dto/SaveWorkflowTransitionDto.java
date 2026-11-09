package com.nexusops.workflow.application.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;
import java.util.UUID;

public record SaveWorkflowTransitionDto(
        UUID id,
        @NotBlank String sourceNodeKey,
        @NotBlank String targetNodeKey,
        Map<String, Object> conditionExpression,
        int priority,
        boolean isDefault
) {}

