package com.nexusops.analytics.application.dto;

import java.util.UUID;

public record AnalyticsOverviewDto(
        long totalRequests,
        long activeRequests,
        long completedRequests,
        long rejectedRequests,
        long cancelledRequests,
        long pendingApprovals,
        long openTasks,
        double avgApprovalCycleHours,
        double avgFulfillmentCycleHours,
        double slaComplianceRate
) {}

