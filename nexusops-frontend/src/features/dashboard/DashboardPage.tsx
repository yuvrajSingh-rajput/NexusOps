import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { FileText, CheckCircle2, Clock, AlertTriangle, ArrowUpRight, Sparkles } from 'lucide-react';
import { apiClient } from '@/lib/api-client';
import { ApiResponse, SystemHealth, AnalyticsOverviewDto } from '@/types/api';

export const DashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const [health, setHealth] = useState<SystemHealth | null>(null);
  const [overview, setOverview] = useState<AnalyticsOverviewDto | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // 1. Fetch system health
    apiClient.get<unknown, ApiResponse<SystemHealth>>('/system/health')
      .then((res) => {
        if (res.data) setHealth(res.data);
      })
      .catch(() => {});

    // 2. Fetch live operational analytics
    apiClient.get<unknown, ApiResponse<AnalyticsOverviewDto>>('/analytics/overview')
      .then((res) => {
        if (res.data) setOverview(res.data);
      })
      .catch((err) => {
        console.error('Failed to load operational analytics', err);
      })
      .finally(() => {
        setLoading(false);
      });
  }, []);

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold text-slate-900 tracking-tight">Operations Dashboard</h1>
          <p className="text-xs sm:text-sm text-slate-500 mt-1">
            Real-time status of service requests, approval workflows, and operational fulfillment.
          </p>
        </div>
        <div className="flex items-center gap-2 self-start sm:self-auto">
          <span className="text-xs px-2.5 py-1 rounded-full bg-slate-100 text-slate-600 font-medium">
            Backend: {health ? `${health.service} (${health.status})` : 'Connecting...'}
          </span>
        </div>
      </div>

      {/* KPI Stats Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4">
        <div
          onClick={() => navigate('/requests')}
          className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm flex items-center justify-between cursor-pointer hover:border-indigo-300 transition-all"
        >
          <div>
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Active Requests</span>
            <div className="text-2xl font-bold text-slate-900 mt-1">
              {loading ? '...' : (overview?.activeRequests ?? 0)}
            </div>
            <span className="text-xs text-indigo-600 flex items-center gap-1 mt-1 font-medium">
              <ArrowUpRight className="h-3.5 w-3.5" /> {overview?.totalRequests ?? 0} total requests
            </span>
          </div>
          <div className="h-11 w-11 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center">
            <FileText className="h-6 w-6" />
          </div>
        </div>

        <div
          onClick={() => navigate('/approvals')}
          className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm flex items-center justify-between cursor-pointer hover:border-amber-300 transition-all"
        >
          <div>
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Pending Approvals</span>
            <div className="text-2xl font-bold text-amber-600 mt-1">
              {loading ? '...' : (overview?.pendingApprovals ?? 0)}
            </div>
            <span className="text-xs text-amber-700 font-medium mt-1 block">Awaiting review</span>
          </div>
          <div className="h-11 w-11 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center">
            <Clock className="h-6 w-6" />
          </div>
        </div>

        <div
          onClick={() => navigate('/tasks')}
          className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm flex items-center justify-between cursor-pointer hover:border-emerald-300 transition-all"
        >
          <div>
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Open Tasks</span>
            <div className="text-2xl font-bold text-slate-900 mt-1">
              {loading ? '...' : (overview?.openTasks ?? 0)}
            </div>
            <span className="text-xs text-slate-500 font-medium mt-1 block">Fulfillment queues</span>
          </div>
          <div className="h-11 w-11 rounded-lg bg-purple-50 text-purple-600 flex items-center justify-center">
            <CheckCircle2 className="h-6 w-6" />
          </div>
        </div>

        <div
          onClick={() => navigate('/analytics')}
          className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm flex items-center justify-between cursor-pointer hover:border-emerald-300 transition-all"
        >
          <div>
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">SLA Adherence</span>
            <div className="text-2xl font-bold text-emerald-600 mt-1">
              {loading ? '...' : `${overview?.slaComplianceRate ?? 100.0}%`}
            </div>
            <span className="text-xs text-emerald-700 font-medium mt-1 block">On-time fulfillment</span>
          </div>
          <div className="h-11 w-11 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center">
            <AlertTriangle className="h-6 w-6" />
          </div>
        </div>
      </div>

      {/* Reference Hardware Purchase Demonstration Card */}
      <div className="bg-gradient-to-r from-slate-900 via-indigo-950 to-slate-900 rounded-2xl p-6 text-white shadow-xl border border-slate-800">
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div>
            <div className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-indigo-500/20 text-indigo-300 border border-indigo-500/30 mb-2">
              <Sparkles className="h-3 w-3" />
              Acceptance Benchmark Flow
            </div>
            <h3 className="text-lg font-bold">Hardware Procurement Workflow</h3>
            <p className="text-sm text-slate-300 mt-1 max-w-2xl leading-relaxed">
              Demonstrates complete enterprise lifecycle: Employee submit → Direct Manager approval → Finance approval (threshold &gt; $1k) → IT Procurement fulfillment task → Completed audit trail.
            </p>
          </div>
          <button
            onClick={() => navigate('/requests')}
            className="px-4 py-2.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-xs font-semibold transition-all shadow-md shadow-indigo-500/20 whitespace-nowrap"
          >
            New Laptop Request
          </button>
        </div>
      </div>
    </div>
  );
};

