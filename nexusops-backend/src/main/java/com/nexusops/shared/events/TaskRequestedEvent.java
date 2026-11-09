package com.nexusops.shared.events;

import java.util.Map;
import java.util.UUID;

public record TaskRequestedEvent(
        UUID organizationId,
        UUID nodeInstanceId,
        UUID workflowInstanceId,
        UUID requestId,
        String taskKey,
        String title,
        Map<String, Object> nodeConfiguration
) {}

