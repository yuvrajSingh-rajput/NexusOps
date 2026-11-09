package com.nexusops.task.application.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TaskHistoryResponseDto(
        UUID id,
        UUID actorMembershipId,
        String actorName,
        String action,
        Map<String, Object> oldState,
        Map<String, Object> newState,
        Instant createdAt
) {}

