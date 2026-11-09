package com.nexusops.task.application.dto;

import com.nexusops.task.domain.TaskPriority;
import com.nexusops.task.domain.TaskStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TaskResponseDto(
        UUID id,
        UUID organizationId,
        String taskNumber,
        String title,
        String description,
        UUID requestId,
        String requestNumber,
        String requestTitle,
        UUID workflowInstanceId,
        UUID nodeInstanceId,
        UUID teamId,
        UUID assigneeMembershipId,
        String assigneeName,
        TaskStatus status,
        TaskPriority priority,
        Map<String, Object> payload,
        Map<String, Object> outputData,
        Instant dueAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt,
        List<TaskCommentResponseDto> comments,
        List<TaskHistoryResponseDto> history
) {}

