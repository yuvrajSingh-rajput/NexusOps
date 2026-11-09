package com.nexusops.analytics.api;

import com.nexusops.analytics.application.AnalyticsService;
import com.nexusops.analytics.application.dto.*;
import com.nexusops.shared.api.ApiResponse;
import com.nexusops.shared.tenant.TenantContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@PreAuthorize("hasAuthority('ANALYTICS_VIEW') or hasAuthority('ROLE_ORG_ADMIN')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<AnalyticsOverviewDto>> getOverview() {
        UUID orgId = TenantContextHolder.requireTenantId();
        AnalyticsOverviewDto dto = analyticsService.getOverview(orgId);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping("/by-status")
    public ResponseEntity<ApiResponse<List<StatusCountDto>>> getByStatus() {
        UUID orgId = TenantContextHolder.requireTenantId();
        List<StatusCountDto> breakdown = analyticsService.getStatusBreakdown(orgId);
        return ResponseEntity.ok(ApiResponse.success(breakdown));
    }

    @GetMapping("/by-category")
    public ResponseEntity<ApiResponse<List<CategoryMetricDto>>> getByCategory() {
        UUID orgId = TenantContextHolder.requireTenantId();
        List<CategoryMetricDto> metrics = analyticsService.getCategoryMetrics(orgId);
        return ResponseEntity.ok(ApiResponse.success(metrics));
    }

    @GetMapping("/by-department")
    public ResponseEntity<ApiResponse<List<DepartmentMetricDto>>> getByDepartment() {
        UUID orgId = TenantContextHolder.requireTenantId();
        List<DepartmentMetricDto> metrics = analyticsService.getDepartmentMetrics(orgId);
        return ResponseEntity.ok(ApiResponse.success(metrics));
    }

    @GetMapping("/department-report")
    public ResponseEntity<ApiResponse<DepartmentReportDto>> getDepartmentReport(
            @RequestParam(required = false, defaultValue = "ALL") String departmentCode) {
        UUID orgId = TenantContextHolder.requireTenantId();
        DepartmentReportDto report = analyticsService.getDepartmentReport(orgId, departmentCode);
        return ResponseEntity.ok(ApiResponse.success(report));
    }
}

