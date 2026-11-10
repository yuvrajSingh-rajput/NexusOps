import React, { useState, useEffect, useMemo } from 'react';
import {
  RefreshCw,
  Clock,
  CheckCircle2,
  Layers,
  Building,
  FileText,
  TrendingUp,
  Copy,
  Check,
  Activity,
  ShieldAlert,
} from 'lucide-react';
import { apiClient } from '@/lib/api-client';
import { useAuthStore } from '@/features/auth/authStore';
import {
  ApiResponse,
  AnalyticsOverviewDto,
  StatusCountDto,
  CategoryMetricDto,
  DepartmentMetricDto,
  DepartmentReportDto,
} from '@/types/api';
import { SEO } from '@/components/common/SEO';

export const AnalyticsPage: React.FC = () => {
  const user = useAuthStore((s) => s.user);
  const canViewAnalytics = useMemo(() => {
    if (!user) return false;
    return (
      user.permissions?.includes('ANALYTICS_VIEW') ||
      user.roles?.includes('ORG_ADMIN') ||
      user.roles?.includes('MANAGER') ||
      user.roles?.includes('FINANCE')
    );
  }, [user]);

  const [overview, setOverview] = useState<AnalyticsOverviewDto | null>(null);
  const [statusBreakdown, setStatusBreakdown] = useState<StatusCountDto[]>([]);
  const [categoryMetrics, setCategoryMetrics] = useState<CategoryMetricDto[]>([]);
  const [departmentMetrics, setDepartmentMetrics] = useState<DepartmentMetricDto[]>([]);
  const [loading, setLoading] = useState(true);

  // Department report generator
  const [selectedDept, setSelectedDept] = useState<string>('ALL');
  const [departmentReport, setDepartmentReport] = useState<DepartmentReportDto | null>(null);
  const [loadingReport, setLoadingReport] = useState(false);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (canViewAnalytics) {
      fetchAllAnalytics();
    }
  }, [canViewAnalytics]);

  const fetchAllAnalytics = async () => {
    setLoading(true);
    try {
      const [overviewRes, statusRes, catRes, deptRes] = await Promise.all([
        apiClient.get<any, ApiResponse<AnalyticsOverviewDto>>('/analytics/overview'),
        apiClient.get<any, ApiResponse<StatusCountDto[]>>('/analytics/by-status'),
        apiClient.get<any, ApiResponse<CategoryMetricDto[]>>('/analytics/by-category'),
        apiClient.get<any, ApiResponse<DepartmentMetricDto[]>>('/analytics/by-department'),
      ]);

      if (overviewRes?.data) setOverview(overviewRes.data);
      if (statusRes?.data) setStatusBreakdown(statusRes.data);
      if (catRes?.data) setCategoryMetrics(catRes.data);
      if (deptRes?.data) setDepartmentMetrics(deptRes.data);

      // Also generate default department report
      fetchDepartmentReport(selectedDept);
    } catch (err) {
      console.error('Failed to load operational analytics', err);
    } finally {
      setLoading(false);
    }
  };

  const fetchDepartmentReport = async (deptCode: string) => {
    setLoadingReport(true);
    try {
      const res = await apiClient.get<any, ApiResponse<DepartmentReportDto>>(
        `/analytics/department-report?departmentCode=${encodeURIComponent(deptCode)}`
      );
      if (res?.data) {
        setDepartmentReport(res.data);
      }
    } catch (err) {
      console.error('Failed to fetch department report', err);
    } finally {
      setLoadingReport(false);
    }
  };

  const handleDeptChange = (dept: string) => {
    setSelectedDept(dept);
    fetchDepartmentReport(dept);
  };

  const handleCopySummary = () => {
    if (departmentReport?.executiveSummary) {
      navigator.clipboard.writeText(departmentReport.executiveSummary);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'COMPLETED':
        return 'bg-emerald-500';
      case 'IN_PROGRESS':
        return 'bg-indigo-500';
      case 'IN_REVIEW':
      case 'WAITING_FOR_INFORMATION':
        return 'bg-amber-500';
      case 'REJECTED':
      case 'CANCELLED':
        return 'bg-rose-500';
      case 'SUBMITTED':
        return 'bg-sky-500';
      default:
        return 'bg-slate-400';
    }
  };

  if (!canViewAnalytics) {
    return (
      <div className="py-20 flex flex-col items-center justify-center text-center px-4">
        <div className="p-3 bg-rose-50 text-rose-600 rounded-2xl mb-4 border border-rose-200">
          <ShieldAlert className="h-10 w-10" />
        </div>
        <h2 className="text-lg font-bold text-slate-900">Access Restricted</h2>
        <p className="text-sm text-slate-500 max-w-md mt-1">
          Operational metrics and bottleneck analytics are restricted to Department Managers, Finance Officers, and Organization Administrators.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <SEO title="Operational Analytics" noIndex={true} />
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Operational Analytics</h1>
            <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-indigo-100 text-indigo-700 border border-indigo-200">
              Live Aggregations
            </span>
          </div>
          <p className="text-sm text-slate-500 mt-1">
            Pre-computed SQL analytics tracking throughput, cycle times, bottleneck queues, and SLA performance.
          </p>
        </div>

        <button
          onClick={fetchAllAnalytics}
          disabled={loading}
          className="inline-flex items-center gap-2 px-3.5 py-2 bg-white border border-slate-200 hover:bg-slate-50 rounded-lg text-xs font-semibold text-slate-700 shadow-sm transition-colors"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
          <span>Refresh Analytics</span>
        </button>
      </div>

      {/* KPI Cards Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Total Volume</span>
            <div className="h-8 w-8 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center">
              <FileText className="h-4 w-4" />
            </div>
          </div>
          <div className="text-2xl font-black text-slate-900 mt-2">
            {overview?.totalRequests ?? 0}
          </div>
          <div className="text-xs text-slate-500 mt-1 flex items-center gap-1.5 font-medium">
            <span className="text-emerald-600 font-semibold">{overview?.completedRequests ?? 0} completed</span>
            <span>•</span>
            <span className="text-indigo-600 font-semibold">{overview?.activeRequests ?? 0} active</span>
          </div>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Approval Cycle</span>
            <div className="h-8 w-8 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center">
              <Clock className="h-4 w-4" />
            </div>
          </div>
          <div className="text-2xl font-black text-slate-900 mt-2">
            {overview?.avgApprovalCycleHours ?? 0.0}h
          </div>
          <div className="text-xs text-slate-500 mt-1 font-medium">
            Average turnaround to decision
          </div>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Fulfillment Cycle</span>
            <div className="h-8 w-8 rounded-lg bg-purple-50 text-purple-600 flex items-center justify-center">
              <TrendingUp className="h-4 w-4" />
            </div>
          </div>
          <div className="text-2xl font-black text-slate-900 mt-2">
            {overview?.avgFulfillmentCycleHours ?? 0.0}h
          </div>
          <div className="text-xs text-slate-500 mt-1 font-medium">
            Operator task completion time
          </div>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">SLA Adherence</span>
            <div className="h-8 w-8 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center">
              <CheckCircle2 className="h-4 w-4" />
            </div>
          </div>
          <div className="text-2xl font-black text-emerald-600 mt-2">
            {overview?.slaComplianceRate ?? 100.0}%
          </div>
          <div className="text-xs text-emerald-700 mt-1 font-medium">
            On-time task fulfillment
          </div>
        </div>
      </div>

      {/* Visual Status Distribution Bar */}
      {statusBreakdown.length > 0 && (
        <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="text-xs font-bold text-slate-700 uppercase tracking-wider">
              Request Status Distribution
            </h3>
            <span className="text-xs text-slate-400 font-medium">
              {overview?.totalRequests ?? 0} total
            </span>
          </div>

          {/* Segmented Progress Meter */}
          <div className="h-3 w-full bg-slate-100 rounded-full overflow-hidden flex shadow-inner">
            {statusBreakdown.map((s) => (
              <div
                key={s.status}
                style={{ width: `${s.percentage}%` }}
                className={`${getStatusColor(s.status)} transition-all`}
                title={`${s.status}: ${s.count} (${s.percentage}%)`}
              />
            ))}
          </div>

          {/* Legend */}
          <div className="flex flex-wrap gap-4 pt-1">
            {statusBreakdown.map((s) => (
              <div key={s.status} className="flex items-center gap-1.5 text-xs text-slate-600 font-medium">
                <span className={`h-2.5 w-2.5 rounded-full ${getStatusColor(s.status)}`} />
                <span>{s.status.replace(/_/g, ' ')}:</span>
                <span className="font-bold text-slate-800">{s.count}</span>
                <span className="text-slate-400 text-[11px]">({s.percentage}%)</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Two-Column Grid: Categories & Department Bottlenecks */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Category Volume & Completion */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Layers className="h-4 w-4 text-indigo-600" />
              <h3 className="text-sm font-bold text-slate-800">Category Throughput</h3>
            </div>
            <span className="text-xs text-slate-400 font-medium">{categoryMetrics.length} categories</span>
          </div>

          <div className="divide-y divide-slate-100">
            {categoryMetrics.length === 0 ? (
              <div className="py-8 text-center text-xs text-slate-400">No categories configured yet.</div>
            ) : (
              categoryMetrics.map((cat) => {
                const completionRate = cat.totalRequests > 0 ? Math.round((cat.completedRequests / cat.totalRequests) * 100) : 0;
                return (
                  <div key={cat.categoryId} className="py-3 flex items-center justify-between gap-4">
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-bold text-slate-800">{cat.categoryName}</span>
                        <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-slate-100 text-slate-500">
                          {cat.categoryKey}
                        </span>
                      </div>
                      {/* Mini Bar */}
                      <div className="h-1.5 w-full bg-slate-100 rounded-full mt-2 overflow-hidden">
                        <div
                          style={{ width: `${completionRate}%` }}
                          className="h-full bg-indigo-600 rounded-full"
                        />
                      </div>
                    </div>
                    <div className="text-right shrink-0">
                      <div className="text-xs font-bold text-slate-800">
                        {cat.completedRequests} / {cat.totalRequests}
                      </div>
                      <div className="text-[10px] text-slate-400 font-medium">
                        {completionRate}% completed
                      </div>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>

        {/* Department Bottlenecks & Queues */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Building className="h-4 w-4 text-purple-600" />
              <h3 className="text-sm font-bold text-slate-800">Department Queues</h3>
            </div>
            <span className="text-xs text-slate-400 font-medium">{departmentMetrics.length} departments</span>
          </div>

          <div className="divide-y divide-slate-100">
            {departmentMetrics.length === 0 ? (
              <div className="py-8 text-center text-xs text-slate-400">No department data recorded yet.</div>
            ) : (
              departmentMetrics.map((dept) => (
                <div key={dept.departmentCode} className="py-3 flex items-center justify-between gap-4">
                  <div>
                    <div className="text-xs font-bold text-slate-800">{dept.departmentName}</div>
                    <div className="text-[10px] text-slate-400 font-mono">@{dept.departmentCode}</div>
                  </div>
                  <div className="flex items-center gap-3">
                    {dept.pendingApprovals > 0 && (
                      <span className="px-2 py-0.5 rounded-full text-[11px] font-semibold bg-amber-50 text-amber-700 border border-amber-200">
                        {dept.pendingApprovals} pending review
                      </span>
                    )}
                    <div className="text-right">
                      <div className="text-xs font-bold text-slate-800">{dept.totalRequests} reqs</div>
                      <div className="text-[10px] text-emerald-600 font-medium">{dept.completedRequests} completed</div>
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      {/* Interactive Executive Briefing & Department Report */}
      <div className="bg-gradient-to-br from-slate-900 via-indigo-950 to-slate-900 rounded-2xl p-6 text-white shadow-xl border border-slate-800 space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <div className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-indigo-500/20 text-indigo-300 border border-indigo-500/30 mb-2">
              <Activity className="h-3 w-3" />
              Executive Decision Briefing
            </div>
            <h3 className="text-lg font-bold">Department Operational Report</h3>
            <p className="text-xs text-slate-300 mt-0.5">
              Automated briefing synthesized from real-time operational database metrics.
            </p>
          </div>

          {/* Department Picker */}
          <div className="flex items-center gap-2">
            <select
              value={selectedDept}
              onChange={(e) => handleDeptChange(e.target.value)}
              className="bg-slate-800 border border-slate-700 text-xs font-semibold text-white rounded-xl px-3 py-2 focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option value="ALL">All Departments (Org-Wide)</option>
              <option value="ENG">Engineering (ENG)</option>
              <option value="IT">Information Technology (IT)</option>
              <option value="HR">Human Resources (HR)</option>
              <option value="FIN">Finance & Accounting (FIN)</option>
              <option value="OPS">Operations (OPS)</option>
            </select>

            <button
              onClick={handleCopySummary}
              className="p-2 bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white rounded-xl border border-slate-700 transition-colors"
              title="Copy executive summary"
            >
              {copied ? <Check className="h-4 w-4 text-emerald-400" /> : <Copy className="h-4 w-4" />}
            </button>
          </div>
        </div>

        {/* Narrative Box */}
        {loadingReport ? (
          <div className="py-6 text-center text-xs text-slate-400">Synthesizing department report...</div>
        ) : departmentReport ? (
          <div className="space-y-4">
            <div className="p-4 rounded-xl bg-slate-800/60 border border-slate-700/60 text-xs leading-relaxed text-slate-200">
              {departmentReport.executiveSummary}
            </div>

            {/* Quick Metrics Bar */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-2">
              <div className="p-3 rounded-xl bg-slate-800/40 border border-slate-700/40">
                <div className="text-[10px] text-slate-400 uppercase tracking-wider font-semibold">Total Requests</div>
                <div className="text-base font-bold text-white mt-0.5">{departmentReport.totalRequests}</div>
              </div>
              <div className="p-3 rounded-xl bg-slate-800/40 border border-slate-700/40">
                <div className="text-[10px] text-slate-400 uppercase tracking-wider font-semibold">Pending Approvals</div>
                <div className="text-base font-bold text-amber-400 mt-0.5">{departmentReport.pendingApprovals}</div>
              </div>
              <div className="p-3 rounded-xl bg-slate-800/40 border border-slate-700/40">
                <div className="text-[10px] text-slate-400 uppercase tracking-wider font-semibold">Avg Turnaround</div>
                <div className="text-base font-bold text-indigo-300 mt-0.5">{departmentReport.avgCycleHours}h</div>
              </div>
              <div className="p-3 rounded-xl bg-slate-800/40 border border-slate-700/40">
                <div className="text-[10px] text-slate-400 uppercase tracking-wider font-semibold">SLA Adherence</div>
                <div className="text-base font-bold text-emerald-400 mt-0.5">{departmentReport.slaComplianceRate}%</div>
              </div>
            </div>
          </div>
        ) : null}
      </div>
    </div>
  );
};

