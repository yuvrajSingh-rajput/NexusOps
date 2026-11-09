package com.nexusops.knowledge.application.dto;

import com.nexusops.knowledge.domain.DocumentAccessScope;
import com.nexusops.knowledge.domain.DocumentProcessingStatus;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponseDto(
        UUID id,
        UUID organizationId,
        String title,
        String description,
        String category,
        DocumentAccessScope accessScope,
        UUID targetScopeId,
        UUID createdByMembershipId,
        Instant createdAt,
        Instant updatedAt,
        int latestVersionNumber,
        DocumentProcessingStatus latestStatus,
        String latestFileName,
        long latestFileSizeBytes,
        int totalChunks
) {}

