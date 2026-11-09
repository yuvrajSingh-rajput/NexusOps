package com.nexusops.knowledge.application.dto;

import jakarta.validation.constraints.NotBlank;

public record SemanticSearchRequestDto(
        @NotBlank(message = "Search query cannot be blank")
        String query,
        Integer limit,
        Double minScore
) {
    public int resolvedLimit() {
        return (limit != null && limit > 0) ? Math.min(limit, 20) : 5;
    }

    public double resolvedMinScore() {
        return (minScore != null && minScore >= 0.0) ? minScore : 0.65;
    }
}

