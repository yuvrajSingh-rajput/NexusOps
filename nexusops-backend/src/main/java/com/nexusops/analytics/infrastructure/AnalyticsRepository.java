package com.nexusops.analytics.infrastructure;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Repository
public class AnalyticsRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AnalyticsRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Long> getRequestCountsByStatus(UUID organizationId) {
        String sql = """
            SELECT status, COUNT(*) AS cnt
            FROM requests
            WHERE organization_id = :orgId
            GROUP BY status
        """;
        var params = new MapSqlParameterSource("orgId", organizationId);
        Map<String, Long> result = new HashMap<>();
        jdbcTemplate.query(sql, params, rs -> {
            result.put(rs.getString("status"), rs.getLong("cnt"));
        });
        return result;
    }

    public long countPendingApprovals(UUID organizationId) {
        String sql = "SELECT COUNT(*) FROM approval_instances WHERE organization_id = :orgId AND status = 'PENDING'";
        var params = new MapSqlParameterSource("orgId", organizationId);
        Long count = jdbcTemplate.queryForObject(sql, params, Long.class);
        return count != null ? count : 0L;
    }

    public long countOpenTasks(UUID organizationId) {
        String sql = "SELECT COUNT(*) FROM tasks WHERE organization_id = :orgId AND status IN ('OPEN', 'IN_PROGRESS', 'BLOCKED')";
        var params = new MapSqlParameterSource("orgId", organizationId);
        Long count = jdbcTemplate.queryForObject(sql, params, Long.class);
        return count != null ? count : 0L;
    }

    public record SlaMetricRow(long totalCompleted, long onTimeCompleted) {}

    public SlaMetricRow getTaskSlaMetrics(UUID organizationId) {
        String sql = """
            SELECT
                COUNT(*) AS total_completed,
                COUNT(CASE WHEN completed_at <= due_at OR due_at IS NULL THEN 1 END) AS on_time
            FROM tasks
            WHERE organization_id = :orgId AND status = 'COMPLETED'
        """;
        var params = new MapSqlParameterSource("orgId", organizationId);
        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) ->
                new SlaMetricRow(rs.getLong("total_completed"), rs.getLong("on_time"))
        );
    }

    public List<Double> getCompletedRequestDurationsHours(UUID organizationId) {
        String sql = """
            SELECT submitted_at, completed_at
            FROM requests
            WHERE organization_id = :orgId
              AND status = 'COMPLETED'
              AND submitted_at IS NOT NULL
              AND completed_at IS NOT NULL
        """;
        var params = new MapSqlParameterSource("orgId", organizationId);
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            Timestamp sub = rs.getTimestamp("submitted_at");
            Timestamp comp = rs.getTimestamp("completed_at");
            if (sub != null && comp != null) {
                return (double) Duration.between(sub.toInstant(), comp.toInstant()).toMinutes() / 60.0;
            }
            return 0.0;
        });
    }

    public List<Double> getCompletedTaskDurationsHours(UUID organizationId) {
        String sql = """
            SELECT created_at, completed_at
            FROM tasks
            WHERE organization_id = :orgId
              AND status = 'COMPLETED'
              AND completed_at IS NOT NULL
        """;
        var params = new MapSqlParameterSource("orgId", organizationId);
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            Timestamp cre = rs.getTimestamp("created_at");
            Timestamp comp = rs.getTimestamp("completed_at");
            if (cre != null && comp != null) {
                return (double) Duration.between(cre.toInstant(), comp.toInstant()).toMinutes() / 60.0;
            }
            return 0.0;
        });
    }

    public record RawCategoryRow(
            UUID categoryId,
            String categoryKey,
            String categoryName,
            long totalRequests,
            long completedRequests
    ) {}

    public List<RawCategoryRow> getCategoryMetrics(UUID organizationId) {
        String sql = """
            SELECT
                rc.id AS category_id,
                rc.code AS category_key,
                rc.name AS category_name,
                COUNT(r.id) AS total_requests,
                COUNT(CASE WHEN r.status = 'COMPLETED' THEN 1 END) AS completed_requests
            FROM request_categories rc
            LEFT JOIN requests r ON r.category_id = rc.id AND r.organization_id = :orgId
            WHERE rc.organization_id = :orgId
            GROUP BY rc.id, rc.code, rc.name
            ORDER BY total_requests DESC
        """;
        var params = new MapSqlParameterSource("orgId", organizationId);
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new RawCategoryRow(
                rs.getObject("category_id", UUID.class),
                rs.getString("category_key"),
                rs.getString("category_name"),
                rs.getLong("total_requests"),
                rs.getLong("completed_requests")
        ));
    }

    public record RawDepartmentRow(
            String departmentCode,
            String departmentName,
            long totalRequests,
            long completedRequests,
            long pendingApprovals
    ) {}

    public List<RawDepartmentRow> getDepartmentMetrics(UUID organizationId) {
        String sql = """
            SELECT
                d.code AS dept_code,
                d.name AS dept_name,
                COUNT(r.id) AS total_requests,
                COUNT(CASE WHEN r.status = 'COMPLETED' THEN 1 END) AS completed_requests,
                COUNT(CASE WHEN r.status IN ('IN_REVIEW', 'WAITING_FOR_INFORMATION') THEN 1 END) AS pending_approvals
            FROM departments d
            LEFT JOIN requests r ON r.department_id = d.id AND r.organization_id = :orgId
            WHERE d.organization_id = :orgId
            GROUP BY d.id, d.code, d.name
            ORDER BY total_requests DESC
        """;
        var params = new MapSqlParameterSource("orgId", organizationId);
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new RawDepartmentRow(
                rs.getString("dept_code"),
                rs.getString("dept_name"),
                rs.getLong("total_requests"),
                rs.getLong("completed_requests"),
                rs.getLong("pending_approvals")
        ));
    }

    public Map<String, Long> getDepartmentStatusBreakdown(UUID organizationId, String departmentCode) {
        String sql = """
            SELECT r.status, COUNT(*) AS cnt
            FROM requests r
            JOIN departments d ON r.department_id = d.id AND d.organization_id = :orgId
            WHERE r.organization_id = :orgId
              AND UPPER(d.code) = UPPER(:deptCode)
            GROUP BY r.status
        """;
        var params = new MapSqlParameterSource()
                .addValue("orgId", organizationId)
                .addValue("deptCode", departmentCode);
        Map<String, Long> result = new HashMap<>();
        jdbcTemplate.query(sql, params, rs -> {
            result.put(rs.getString("status"), rs.getLong("cnt"));
        });
        return result;
    }
}

