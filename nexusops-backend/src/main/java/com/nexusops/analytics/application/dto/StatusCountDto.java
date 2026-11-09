package com.nexusops.analytics.application.dto;

public record StatusCountDto(
        String status,
        long count,
        double percentage
) {}

