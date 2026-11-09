package com.nexusops.analytics.application.dto;

public record DepartmentMetricDto(
        String departmentCode,
        String departmentName,
        long totalRequests,
        long completedRequests,
        long pendingApprovals,
        double avgCycleHours
) {}

