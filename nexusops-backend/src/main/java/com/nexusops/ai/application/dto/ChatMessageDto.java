package com.nexusops.ai.application.dto;

import com.nexusops.ai.domain.AiMessageRole;
import com.nexusops.knowledge.application.dto.SearchResultDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatMessageDto(
        UUID id,
        AiMessageRole role,
        String content,
        List<SearchResultDto> citations,
        ActionProposalDto proposal,
        Instant createdAt
) {}

