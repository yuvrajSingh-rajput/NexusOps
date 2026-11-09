package com.nexusops.analytics.application.dto;

import java.util.UUID;

public record CategoryMetricDto(
        UUID categoryId,
        String categoryKey,
        String categoryName,
        long totalRequests,
        long completedRequests,
        double avgCycleHours
) {}

