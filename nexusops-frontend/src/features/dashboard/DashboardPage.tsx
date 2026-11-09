import React, { useEffect, useState } from 'react';
import { FileText, CheckCircle2, Clock, AlertTriangle, ArrowUpRight } from 'lucide-react';
import { apiClient } from '@/lib/api-client';
import { ApiResponse, SystemHealth } from '@/types/api';

export const DashboardPage: React.FC = () => {
  const [health, setHealth] = useState<SystemHealth | null>(null);

  useEffect(() => {
    apiClient.get<unknown, ApiResponse<SystemHealth>>('/system/health')
      .then((res) => {
        if (res.data) setHealth(res.data);
      })
      .catch(() => {
        // Backend might still be starting or in test mode
      });
  }, []);

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Operations Dashboard</h1>
          <p className="text-sm text-slate-500 mt-1">
            Real-time status of service requests, approval workflows, and operational fulfillment.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-xs px-2.5 py-1 rounded-full bg-slate-100 text-slate-600 font-medium">
            Backend: {health ? `${health.service} (${health.status})` : 'Connecting...'}
          </span>
        </div>
      </div>

      {/* KPI Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm flex items-center justify-between">
          <div>
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Active Requests</span>
            <div className="text-2xl font-bold text-slate-900 mt-1">12</div>
            <span className="text-xs text-emerald-600 flex items-center gap-1 mt-1 font-medium">
              <ArrowUpRight className="h-3.5 w-3.5" /> +3 this week
            </span>
          </div>
          <div className="h-11 w-11 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center">
            <FileText className="h-6 w-6" />
          </div>
        </div>

        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm flex items-center justify-between">
          <div>
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Pending Approvals</span>
            <div className="text-2xl font-bold text-amber-600 mt-1">4</div>
            <span className="text-xs text-amber-700 font-medium mt-1 block">Awaiting your action: 2</span>
          </div>
          <div className="h-11 w-11 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center">
            <Clock className="h-6 w-6" />
          </div>
        </div>

        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm flex items-center justify-between">
          <div>
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Open Tasks</span>
            <div className="text-2xl font-bold text-slate-900 mt-1">8</div>
            <span className="text-xs text-slate-500 font-medium mt-1 block">IT & Procurement queues</span>
          </div>
          <div className="h-11 w-11 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center">
            <CheckCircle2 className="h-6 w-6" />
          </div>
        </div>

        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm flex items-center justify-between">
          <div>
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">SLA Adherence</span>
            <div className="text-2xl font-bold text-emerald-600 mt-1">98.4%</div>
            <span className="text-xs text-emerald-700 font-medium mt-1 block">0 Breaches this month</span>
          </div>
          <div className="h-11 w-11 rounded-lg bg-slate-50 text-slate-600 flex items-center justify-center">
            <AlertTriangle className="h-6 w-6" />
          </div>
        </div>
      </div>

      {/* Reference Hardware Purchase Demonstration Card */}
      <div className="bg-gradient-to-r from-slate-900 to-indigo-950 rounded-xl p-6 text-white shadow-lg">
        <div className="flex items-start justify-between">
          <div>
            <div className="inline-block px-2.5 py-0.5 rounded text-[11px] font-bold bg-indigo-500/20 text-indigo-300 border border-indigo-500/30 mb-2">
              Acceptance Benchmark Flow
            </div>
            <h3 className="text-lg font-bold">Hardware Procurement Workflow</h3>
            <p className="text-sm text-slate-300 mt-1 max-w-2xl">
              Demonstrates complete enterprise lifecycle: Employee submit → Direct Manager approval → Finance approval (threshold &gt; $1k) → IT Procurement fulfillment task → Completed audit trail.
            </p>
          </div>
          <button className="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-xs font-semibold transition-colors shadow-md">
            New Laptop Request
          </button>
        </div>
      </div>
    </div>
  );
};

