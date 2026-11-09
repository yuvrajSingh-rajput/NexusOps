package com.nexusops.knowledge.application.dto;

import java.util.UUID;

public record SearchResultDto(
        UUID chunkId,
        UUID documentId,
        String documentTitle,
        Integer pageNumber,
        int chunkIndex,
        double similarityScore,
        String content,
        String citation
) {}

