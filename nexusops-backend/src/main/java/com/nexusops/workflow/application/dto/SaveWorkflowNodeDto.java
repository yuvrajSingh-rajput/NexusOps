package com.nexusops.workflow.application.dto;

import com.nexusops.workflow.domain.NodeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record SaveWorkflowNodeDto(
        UUID id,
        @NotBlank String nodeKey,
        @NotNull NodeType nodeType,
        @NotBlank String name,
        Map<String, Object> configuration,
        double positionX,
        double positionY
) {}

