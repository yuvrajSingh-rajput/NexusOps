package com.nexusops.analytics.application;

import com.nexusops.analytics.application.dto.*;
import com.nexusops.analytics.infrastructure.AnalyticsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private final AnalyticsRepository analyticsRepository;

    public AnalyticsService(AnalyticsRepository analyticsRepository) {
        this.analyticsRepository = analyticsRepository;
    }

    public AnalyticsOverviewDto getOverview(UUID organizationId) {
        Map<String, Long> statusCounts = analyticsRepository.getRequestCountsByStatus(organizationId);

        long total = statusCounts.values().stream().mapToLong(Long::longValue).sum();
        long completed = statusCounts.getOrDefault("COMPLETED", 0L);
        long rejected = statusCounts.getOrDefault("REJECTED", 0L);
        long cancelled = statusCounts.getOrDefault("CANCELLED", 0L);
        long active = total - completed - rejected - cancelled;

        long pendingApprovals = analyticsRepository.countPendingApprovals(organizationId);
        long openTasks = analyticsRepository.countOpenTasks(organizationId);

        List<Double> reqDurations = analyticsRepository.getCompletedRequestDurationsHours(organizationId);
        double avgApprovalCycleHours = reqDurations.isEmpty() ? 0.0 : round(reqDurations.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));

        List<Double> taskDurations = analyticsRepository.getCompletedTaskDurationsHours(organizationId);
        double avgFulfillmentHours = taskDurations.isEmpty() ? 0.0 : round(taskDurations.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));

        AnalyticsRepository.SlaMetricRow slaRow = analyticsRepository.getTaskSlaMetrics(organizationId);
        double slaRate = slaRow.totalCompleted() == 0 ? 100.0 : round(((double) slaRow.onTimeCompleted() / slaRow.totalCompleted()) * 100.0);

        return new AnalyticsOverviewDto(
                total,
                Math.max(0, active),
                completed,
                rejected,
                cancelled,
                pendingApprovals,
                openTasks,
                avgApprovalCycleHours,
                avgFulfillmentHours,
                slaRate
        );
    }

    public List<StatusCountDto> getStatusBreakdown(UUID organizationId) {
        Map<String, Long> counts = analyticsRepository.getRequestCountsByStatus(organizationId);
        long total = counts.values().stream().mapToLong(Long::longValue).sum();

        List<StatusCountDto> breakdown = new ArrayList<>();
        counts.forEach((status, count) -> {
            double pct = total == 0 ? 0.0 : round(((double) count / total) * 100.0);
            breakdown.add(new StatusCountDto(status, count, pct));
        });

        breakdown.sort(Comparator.comparing(StatusCountDto::count).reversed());
        return breakdown;
    }

    public List<CategoryMetricDto> getCategoryMetrics(UUID organizationId) {
        List<AnalyticsRepository.RawCategoryRow> rows = analyticsRepository.getCategoryMetrics(organizationId);
        List<Double> durations = analyticsRepository.getCompletedRequestDurationsHours(organizationId);
        double avgHours = durations.isEmpty() ? 0.0 : round(durations.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));

        return rows.stream().map(r -> new CategoryMetricDto(
                r.categoryId(),
                r.categoryKey(),
                r.categoryName(),
                r.totalRequests(),
                r.completedRequests(),
                avgHours
        )).toList();
    }

    public List<DepartmentMetricDto> getDepartmentMetrics(UUID organizationId) {
        List<AnalyticsRepository.RawDepartmentRow> rows = analyticsRepository.getDepartmentMetrics(organizationId);
        List<Double> durations = analyticsRepository.getCompletedRequestDurationsHours(organizationId);
        double avgHours = durations.isEmpty() ? 0.0 : round(durations.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));

        return rows.stream().map(r -> new DepartmentMetricDto(
                r.departmentCode(),
                r.departmentName() != null ? r.departmentName() : mapDepartmentName(r.departmentCode()),
                r.totalRequests(),
                r.completedRequests(),
                r.pendingApprovals(),
                avgHours
        )).toList();
    }

    public DepartmentReportDto getDepartmentReport(UUID organizationId, String departmentCode) {
        String deptCode = (departmentCode != null && !departmentCode.isBlank()) ? departmentCode.toUpperCase().trim() : "ALL";
        String deptName = mapDepartmentName(deptCode);

        Map<String, Long> statusMap = deptCode.equals("ALL")
                ? analyticsRepository.getRequestCountsByStatus(organizationId)
                : analyticsRepository.getDepartmentStatusBreakdown(organizationId, deptCode);

        long total = statusMap.values().stream().mapToLong(Long::longValue).sum();
        long completed = statusMap.getOrDefault("COMPLETED", 0L);
        long pending = statusMap.getOrDefault("IN_REVIEW", 0L) + statusMap.getOrDefault("WAITING_FOR_INFORMATION", 0L);
        long openTasks = analyticsRepository.countOpenTasks(organizationId);

        List<Double> durations = analyticsRepository.getCompletedRequestDurationsHours(organizationId);
        double avgCycleHours = durations.isEmpty() ? 0.0 : round(durations.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));

        AnalyticsRepository.SlaMetricRow slaRow = analyticsRepository.getTaskSlaMetrics(organizationId);
        double slaRate = slaRow.totalCompleted() == 0 ? 100.0 : round(((double) slaRow.onTimeCompleted() / slaRow.totalCompleted()) * 100.0);

        List<StatusCountDto> breakdown = new ArrayList<>();
        statusMap.forEach((status, count) -> {
            double pct = total == 0 ? 0.0 : round(((double) count / total) * 100.0);
            breakdown.add(new StatusCountDto(status, count, pct));
        });
        breakdown.sort(Comparator.comparing(StatusCountDto::count).reversed());

        String summary = generateExecutiveSummary(deptName, total, completed, pending, avgCycleHours, slaRate);

        return new DepartmentReportDto(
                deptCode,
                deptName,
                total,
                completed,
                pending,
                openTasks,
                avgCycleHours,
                slaRate,
                breakdown,
                summary,
                Instant.now()
        );
    }

    private String generateExecutiveSummary(String deptName, long total, long completed, long pending, double avgHours, double slaRate) {
        double completionRate = total > 0 ? round(((double) completed / total) * 100.0) : 0.0;
        return String.format(
                "Operational Report for %s: Total requests logged: %d, with %d successfully fulfilled (%.1f%% completion rate). " +
                "Currently, %d approvals remain pending review. The average fulfillment cycle time is %.1f hours with an SLA compliance rate of %.1f%%.",
                deptName, total, completed, completionRate, pending, avgHours, slaRate
        );
    }

    private String mapDepartmentName(String code) {
        if (code == null) return "General Operations";
        return switch (code.toUpperCase()) {
            case "ENG" -> "Engineering";
            case "IT" -> "Information Technology";
            case "HR" -> "Human Resources";
            case "FIN" -> "Finance & Accounting";
            case "OPS" -> "Operations & Facilities";
            case "ALL" -> "All Departments (Organization-Wide)";
            default -> code.length() <= 4 ? code + " Department" : code;
        };
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}

