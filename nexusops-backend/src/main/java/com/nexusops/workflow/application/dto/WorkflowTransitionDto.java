package com.nexusops.workflow.application.dto;

import java.util.Map;
import java.util.UUID;

public record WorkflowTransitionDto(
        UUID id,
        UUID sourceNodeId,
        UUID targetNodeId,
        Map<String, Object> conditionExpression,
        int priority,
        boolean isDefault
) {}
