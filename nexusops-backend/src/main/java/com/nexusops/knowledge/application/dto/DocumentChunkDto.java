package com.nexusops.knowledge.application.dto;

import java.util.UUID;

public record DocumentChunkDto(
        UUID id,
        int chunkIndex,
        String content,
        Integer pageNumber,
        int tokenCount
) {}

