package com.nexusops.ai.application.dto;

import com.nexusops.ai.domain.ToolExecutionStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ActionProposalDto(
        UUID actionId,
        String toolName,
        String title,
        String description,
        Map<String, Object> parameters,
        Instant expiresAt,
        ToolExecutionStatus status
) {}

