import React, { useState, useEffect, useMemo } from 'react';
import {
  Search,
  RefreshCw,
  Code,
  User,
  X,
  ShieldAlert,
} from 'lucide-react';
import { apiClient } from '@/lib/api-client';
import { useAuthStore } from '@/features/auth/authStore';
import { ApiResponse, PageResponse, AuditLogResponseDto } from '@/types/api';

export const AuditTrailPage: React.FC = () => {
  const user = useAuthStore((s) => s.user);
  const canViewAudit = useMemo(() => {
    if (!user) return false;
    return user.permissions?.includes('AUDIT_VIEW') || user.roles?.includes('ORG_ADMIN');
  }, [user]);

  const [logs, setLogs] = useState<AuditLogResponseDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedType, setSelectedType] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  // Inspector modal for JSON before/after state
  const [inspectLog, setInspectLog] = useState<AuditLogResponseDto | null>(null);

  useEffect(() => {
    if (canViewAudit) {
      fetchAuditLogs();
    }
  }, [page, selectedType, canViewAudit]);

  const fetchAuditLogs = async () => {
    setLoading(true);
    try {
      let url = `/audit/logs?page=${page}&size=15`;
      if (selectedType !== 'ALL') {
        url += `&aggregateType=${selectedType}`;
      }
      const res = await apiClient.get<any, ApiResponse<PageResponse<AuditLogResponseDto>>>(url);
      if (res?.data?.content) {
        setLogs(res.data.content);
        setTotalPages(res.data.totalPages || 1);
        setTotalElements(res.data.totalElements || 0);
      }
    } catch (err) {
      console.error('Failed to fetch audit logs', err);
    } finally {
      setLoading(false);
    }
  };

  const filteredLogs = logs.filter((log) => {
    if (!searchQuery) return true;
    const q = searchQuery.toLowerCase();
    return (
      (log.eventType || '').toLowerCase().includes(q) ||
      (log.aggregateType || '').toLowerCase().includes(q) ||
      (log.aggregateId || '').toLowerCase().includes(q) ||
      (log.correlationId && log.correlationId.toLowerCase().includes(q))
    );
  });

  const getEventBadgeColor = (eventType: string) => {
    if (eventType.includes('COMPLETED') || eventType.includes('APPROVED')) {
      return 'bg-emerald-50 text-emerald-700 border-emerald-200';
    }
    if (eventType.includes('REJECTED') || eventType.includes('CANCELLED')) {
      return 'bg-rose-50 text-rose-700 border-rose-200';
    }
    if (eventType.includes('SUBMITTED') || eventType.includes('CREATED')) {
      return 'bg-indigo-50 text-indigo-700 border-indigo-200';
    }
    return 'bg-slate-50 text-slate-700 border-slate-200';
  };

  const parseJsonSafe = (raw?: string) => {
    if (!raw) return null;
    try {
      return JSON.parse(raw);
    } catch {
      return raw;
    }
  };

  if (!canViewAudit) {
    return (
      <div className="py-20 flex flex-col items-center justify-center text-center px-4">
        <div className="p-3 bg-rose-50 text-rose-600 rounded-2xl mb-4 border border-rose-200">
          <ShieldAlert className="h-10 w-10" />
        </div>
        <h2 className="text-lg font-bold text-slate-900">Access Restricted</h2>
        <p className="text-sm text-slate-500 max-w-md mt-1">
          The immutable system audit trail is restricted to Organization Administrators and authorized Compliance Auditors.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Audit Trail</h1>
            <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800 border border-emerald-200">
              Immutable
            </span>
          </div>
          <p className="text-sm text-slate-500 mt-1">
            Tamper-proof append-only ledger tracking all business transactions and state transitions.
          </p>
        </div>

        <button
          onClick={fetchAuditLogs}
          disabled={loading}
          className="inline-flex items-center gap-2 px-3.5 py-2 bg-white border border-slate-200 hover:bg-slate-50 rounded-lg text-xs font-semibold text-slate-700 shadow-sm transition-colors"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
          <span>Refresh</span>
        </button>
      </div>

      {/* Filter Bar */}
      <div className="flex flex-wrap items-center justify-between gap-3 bg-white p-3.5 rounded-xl border border-slate-200 shadow-sm">
        {/* Aggregate Type Pills */}
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 sm:pb-0">
          {['ALL', 'REQUEST', 'APPROVAL', 'TASK', 'DOCUMENT', 'WORKFLOW'].map((type) => (
            <button
              key={type}
              onClick={() => {
                setSelectedType(type);
                setPage(0);
              }}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                selectedType === type
                  ? 'bg-indigo-600 text-white shadow-sm'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200/70'
              }`}
            >
              {type}
            </button>
          ))}
        </div>

        {/* Search */}
        <div className="relative w-full sm:w-64">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-slate-400" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search event, aggregate ID..."
            className="w-full pl-8 pr-3 py-1.5 text-xs bg-slate-50 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
          />
        </div>
      </div>

      {/* Table */}
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs text-slate-600">
            <thead className="bg-slate-50 border-b border-slate-200 text-slate-400 uppercase tracking-wider font-semibold text-[11px]">
              <tr>
                <th className="py-3 px-4">Event &amp; Action</th>
                <th className="py-3 px-4">Aggregate</th>
                <th className="py-3 px-4">Actor</th>
                <th className="py-3 px-4">Timestamp (UTC)</th>
                <th className="py-3 px-4">Correlation ID</th>
                <th className="py-3 px-4 text-right">State Snapshot</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading && logs.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-slate-400">
                    Loading audit trail records...
                  </td>
                </tr>
              ) : filteredLogs.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-slate-400">
                    No audit records match the selected criteria.
                  </td>
                </tr>
              ) : (
                filteredLogs.map((log) => (
                  <tr key={log.id} className="hover:bg-slate-50/60 transition-colors">
                    {/* Event Type */}
                    <td className="py-3 px-4">
                      <span
                        className={`inline-block px-2.5 py-1 rounded-md text-[11px] font-bold border ${getEventBadgeColor(
                          log.eventType
                        )}`}
                      >
                        {log.eventType}
                      </span>
                    </td>

                    {/* Aggregate */}
                    <td className="py-3 px-4">
                      <div className="font-semibold text-slate-800">{log.aggregateType}</div>
                      <div className="text-[10px] text-slate-400 font-mono truncate max-w-[140px]">
                        {log.aggregateId}
                      </div>
                    </td>

                    {/* Actor */}
                    <td className="py-3 px-4">
                      {log.actorMembershipId ? (
                        <div className="flex items-center gap-1.5 text-slate-700">
                          <User className="h-3.5 w-3.5 text-slate-400" />
                          <span className="font-mono text-[10px] truncate max-w-[120px]">
                            {log.actorMembershipId}
                          </span>
                        </div>
                      ) : (
                        <span className="text-slate-400 italic">System Event</span>
                      )}
                    </td>

                    {/* Timestamp */}
                    <td className="py-3 px-4 font-mono text-slate-500 text-[11px]">
                      {new Date(log.createdAt).toLocaleString()}
                    </td>

                    {/* Correlation ID */}
                    <td className="py-3 px-4">
                      {log.correlationId ? (
                        <span className="font-mono text-[10px] text-slate-400 truncate block max-w-[120px]" title={log.correlationId}>
                          {log.correlationId}
                        </span>
                      ) : (
                        <span className="text-slate-300">-</span>
                      )}
                    </td>

                    {/* Inspect Button */}
                    <td className="py-3 px-4 text-right">
                      <button
                        onClick={() => setInspectLog(log)}
                        className="inline-flex items-center gap-1 px-2.5 py-1 bg-slate-100 hover:bg-indigo-50 hover:text-indigo-600 rounded-md text-[11px] font-semibold text-slate-700 transition-colors"
                      >
                        <Code className="h-3 w-3" />
                        <span>Inspect</span>
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination Footer */}
        {totalPages > 1 && (
          <div className="p-3 bg-slate-50 border-t border-slate-200 flex items-center justify-between text-xs text-slate-500">
            <div>
              Total: <span className="font-semibold text-slate-800">{totalElements}</span> entries
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                className="px-2.5 py-1 rounded bg-white border border-slate-200 disabled:opacity-50 font-semibold"
              >
                Previous
              </button>
              <span className="font-medium">
                Page {page + 1} of {totalPages}
              </span>
              <button
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                className="px-2.5 py-1 rounded bg-white border border-slate-200 disabled:opacity-50 font-semibold"
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>

      {/* JSON State Inspector Modal */}
      {inspectLog && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-in fade-in duration-100">
          <div className="w-full max-w-2xl bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden flex flex-col max-h-[85vh]">
            {/* Header */}
            <div className="p-4 bg-slate-50 border-b border-slate-200 flex items-center justify-between">
              <div>
                <h3 className="text-sm font-bold text-slate-800">
                  Audit Snapshot: {inspectLog.eventType}
                </h3>
                <p className="text-xs text-slate-400 font-mono mt-0.5">
                  Aggregate: {inspectLog.aggregateType} ({inspectLog.aggregateId})
                </p>
              </div>
              <button
                onClick={() => setInspectLog(null)}
                className="p-1 rounded-lg hover:bg-slate-200 text-slate-400 hover:text-slate-600 transition-colors"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            {/* Content */}
            <div className="p-5 space-y-4 overflow-y-auto">
              {inspectLog.beforeState && (
                <div>
                  <h4 className="text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                    Before State
                  </h4>
                  <pre className="p-3 bg-slate-900 text-slate-100 rounded-xl text-xs font-mono overflow-x-auto">
                    {JSON.stringify(parseJsonSafe(inspectLog.beforeState), null, 2)}
                  </pre>
                </div>
              )}

              <div>
                <h4 className="text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                  After State Payload
                </h4>
                <pre className="p-3 bg-slate-900 text-emerald-400 rounded-xl text-xs font-mono overflow-x-auto">
                  {JSON.stringify(parseJsonSafe(inspectLog.afterState), null, 2)}
                </pre>
              </div>
            </div>

            {/* Footer */}
            <div className="p-4 bg-slate-50 border-t border-slate-200 flex justify-end">
              <button
                onClick={() => setInspectLog(null)}
                className="px-4 py-2 bg-slate-800 hover:bg-slate-900 text-white rounded-lg text-xs font-semibold transition-colors"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

