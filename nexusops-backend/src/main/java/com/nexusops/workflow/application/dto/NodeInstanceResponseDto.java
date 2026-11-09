package com.nexusops.workflow.application.dto;

import com.nexusops.workflow.domain.NodeInstanceStatus;
import com.nexusops.workflow.domain.NodeType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NodeInstanceResponseDto(
        UUID id,
        UUID workflowInstanceId,
        UUID nodeId,
        String nodeKey,
        String title,
        NodeType nodeType,
        NodeInstanceStatus status,
        Map<String, Object> result,
        Instant enteredAt,
        Instant exitedAt
) {}

