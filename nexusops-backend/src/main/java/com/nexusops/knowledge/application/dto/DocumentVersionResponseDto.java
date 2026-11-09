package com.nexusops.knowledge.application.dto;

import com.nexusops.knowledge.domain.DocumentProcessingStatus;

import java.time.Instant;
import java.util.UUID;

public record DocumentVersionResponseDto(
        UUID id,
        UUID documentId,
        int versionNumber,
        String fileName,
        long fileSizeBytes,
        String mimeType,
        DocumentProcessingStatus processingStatus,
        String errorMessage,
        int chunkCount,
        Instant createdAt,
        Instant processedAt
) {}

