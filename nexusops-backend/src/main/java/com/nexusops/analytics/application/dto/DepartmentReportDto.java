package com.nexusops.analytics.application.dto;

import java.time.Instant;
import java.util.List;

public record DepartmentReportDto(
        String departmentCode,
        String departmentName,
        long totalRequests,
        long completedRequests,
        long pendingApprovals,
        long openTasks,
        double avgCycleHours,
        double slaComplianceRate,
        List<StatusCountDto> statusBreakdown,
        String executiveSummary,
        Instant generatedAt
) {}

