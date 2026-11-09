package com.nexusops.ai.application.dto;

import com.nexusops.knowledge.application.dto.SearchResultDto;

import java.util.List;
import java.util.UUID;

public record ChatResponseDto(
        UUID conversationId,
        UUID messageId,
        String reply,
        List<SearchResultDto> citations,
        ActionProposalDto proposal,
        String toolUsed
) {
}

