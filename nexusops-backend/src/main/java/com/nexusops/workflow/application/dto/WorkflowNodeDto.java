package com.nexusops.workflow.application.dto;

import com.nexusops.workflow.domain.NodeType;
import java.util.Map;
import java.util.UUID;

public record WorkflowNodeDto(
        UUID id,
        String nodeKey,
        NodeType nodeType,
        String title,
        Map<String, Object> configuration,
        double positionX,
        double positionY
) {}
