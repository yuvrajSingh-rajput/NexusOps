package com.nexusops.ai.application.tools;

import com.nexusops.analytics.application.AnalyticsService;
import com.nexusops.analytics.application.dto.DepartmentReportDto;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GenerateDepartmentReportTool implements NexusAiTool {

    private final AnalyticsService analyticsService;

    public GenerateDepartmentReportTool(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @Override
    public String getName() {
        return "generateDepartmentReport";
    }

    @Override
    public String getDescription() {
        return "Generate operational metrics, request throughput, and SLA compliance statistics for a specified department (e.g. ENG, IT, HR, FIN) or organization-wide.";
    }

    @Override
    public String getRequiredPermission() {
        return null; // Available to all authenticated tenant members
    }

    @Override
    public boolean isWriteAction() {
        return false;
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> arguments, ToolExecutionContext context) {
        try {
            String departmentCode = "ALL";
            if (arguments != null && arguments.containsKey("departmentCode") && arguments.get("departmentCode") != null) {
                departmentCode = arguments.get("departmentCode").toString();
            }

            DepartmentReportDto report = analyticsService.getDepartmentReport(
                    context.principal().getOrganizationId(),
                    departmentCode
            );

            return ToolExecutionResult.success(report, report.executiveSummary());
        } catch (Exception e) {
            return ToolExecutionResult.failure("Failed to generate department report: " + e.getMessage());
        }
    }
}

