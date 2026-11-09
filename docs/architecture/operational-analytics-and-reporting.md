# Operational Analytics, Reporting & SLA Engine

## 1. Overview & Architectural Principles

NexusOps provides enterprise operational visibility across service requests, approval workflows, task fulfillment queues, and SLA performance.
Analytics follow strict architectural boundaries:
- **Zero Raw SQL from AI:** Nexus AI never generates, executes, or modifies SQL statements. Metrics are strictly computed by pre-tested Java/Spring services using deterministic SQL aggregations against tenant-scoped tables.
- **Tenant Isolation:** Every aggregation query is strictly bounded by `organization_id = TenantContextHolder.requireTenantId()`. Cross-tenant data leakage is impossible.
- **CQRS Read Performance:** Fast analytical aggregation queries leverage ANSI SQL `COUNT`, `SUM(CASE ...)`, and `GROUP BY`, coupled with in-memory duration calculations using `java.time.Duration` for clean cross-database compatibility (PostgreSQL and H2).

---

## 2. Metrics & Calculation Formulas

### A. Overview KPIs
- **Total Requests:** `COUNT(*)` from `requests` for the tenant.
- **Active Requests:** `COUNT(*)` where `status IN ('SUBMITTED', 'IN_REVIEW', 'WAITING_FOR_INFORMATION', 'IN_PROGRESS', 'ON_HOLD')`.
- **Completed Requests:** `COUNT(*)` where `status = 'COMPLETED'`.
- **Pending Approvals:** `COUNT(*)` from `approval_instances` where `status = 'PENDING'`.
- **Open Tasks:** `COUNT(*)` from `tasks` where `status IN ('OPEN', 'IN_PROGRESS', 'BLOCKED')`.
- **Approval Cycle Time (Hours):** Average `Duration.between(submitted_at, completed_at)` for completed requests.
- **Task Fulfillment Time (Hours):** Average `Duration.between(created_at, completed_at)` for completed fulfillment tasks.
- **SLA Compliance Rate (%):**
  $$\text{SLA Compliance} = \frac{\text{Completed Tasks with } (\text{completed\_at} \le \text{due\_at} \lor \text{due\_at IS NULL})}{\text{Total Completed Tasks}} \times 100$$

### B. Status Distribution & Category Throughput
- **Status Breakdown:** Grouped by `status` with count and exact percentage.
- **Category Metrics:** Grouped by `request_categories` (`code`, `name`) with total volume, completed volume, and completion percentage.

### C. Department Queues & Bottleneck Heatmap
- Grouped by `departments` (`code`, `name`):
  - Total requests submitted by department members.
  - Completed request count.
  - Active pending approvals awaiting action by department managers.

---

## 3. Nexus AI Integration (`generateDepartmentReport`)

When executives or managers ask Nexus AI:
> *"Generate a department report for Engineering"* or *"What are our operational metrics?"*

1. **Intent Classification:** `IntentClassifier` detects `DEPARTMENT_REPORT` intent.
2. **Department Resolution:** Maps text tokens (e.g., "Engineering", "IT", "HR", "Finance") to official department codes (`ENG`, `IT`, `HR`, `FIN`, `OPS`, or `ALL`).
3. **Pre-computed SQL Query:** `AnalyticsService.getDepartmentReport(orgId, deptCode)` executes read-only SQL aggregations.
4. **Grounded Narrative Synthesis:** The LLM receives the structured `DepartmentReportDto` and synthesizes an executive decision briefing card without touching raw SQL.

---

## 4. REST API Contracts (`/api/v1/analytics`)

| Method | Endpoint | Description | Response Data |
|---|---|---|---|
| `GET` | `/overview` | Overall operational KPIs | `AnalyticsOverviewDto` |
| `GET` | `/by-status` | Request volume by status | `List<StatusCountDto>` |
| `GET` | `/by-category` | Throughput by request category | `List<CategoryMetricDto>` |
| `GET` | `/by-department` | Queue volume and pending approvals | `List<DepartmentMetricDto>` |
| `GET` | `/department-report` | Executive summary and department metrics | `DepartmentReportDto` |

