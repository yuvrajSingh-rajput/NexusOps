package com.nexusops.shared.events;

import java.util.Map;
import java.util.UUID;

public record ApprovalRequestedEvent(
        UUID organizationId,
        UUID nodeInstanceId,
        UUID requestId,
        Map<String, Object> nodeConfiguration,
        int timeoutHours
) {}

