import React, { useEffect, useState, useMemo } from 'react';
import {
  CheckCircle2,
  XCircle,
  HelpCircle,
  Clock,
  Search,
  RefreshCw,
  AlertCircle,
  User,
  Building,
  Calendar,
  DollarSign,
  Send,
  Layers,
  ChevronRight
} from 'lucide-react';
import { apiClient } from '@/lib/api-client';
import {
  ApiResponse,
  ApprovalInstanceResponseDto,
  ApprovalDecisionDto,
  ApprovalActionType,
} from '@/types/api';
import { SEO } from '@/components/common/SEO';

export const ApprovalsPage: React.FC = () => {
  const [approvals, setApprovals] = useState<ApprovalInstanceResponseDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Search & Filter
  const [searchQuery, setSearchQuery] = useState('');
  const [filterTab, setFilterTab] = useState<'PENDING' | 'ALL'>('PENDING');

  // Selected Approval Modal / Drawer
  const [selectedApproval, setSelectedApproval] = useState<ApprovalInstanceResponseDto | null>(null);
  const [decisionAction, setDecisionAction] = useState<ApprovalActionType | null>(null);
  const [comments, setComments] = useState('');
  const [submittingDecision, setSubmittingDecision] = useState(false);
  const [decisionError, setDecisionError] = useState<string | null>(null);

  const fetchApprovals = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<unknown, ApiResponse<ApprovalInstanceResponseDto[]>>('/approvals/pending');
      if (res.data) {
        setApprovals(res.data);
      }
    } catch (err: any) {
      console.error('Failed to load approvals:', err);
      setError(err?.message || 'Failed to load approvals from server.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchApprovals();
  }, []);

  const handleOpenDecision = (approval: ApprovalInstanceResponseDto, action?: ApprovalActionType) => {
    setSelectedApproval(approval);
    setDecisionAction(action || null);
    setComments('');
    setDecisionError(null);
  };

  const handleExecuteDecision = async (action: ApprovalActionType) => {
    if (!selectedApproval) return;

    if (action === 'REJECT' && !comments.trim()) {
      setDecisionError('A clear justification comment is required when rejecting a request.');
      return;
    }

    setSubmittingDecision(true);
    setDecisionError(null);

    const dto: ApprovalDecisionDto = {
      action,
      comments: comments.trim() || undefined,
    };

    try {
      const res = await apiClient.post<unknown, ApiResponse<ApprovalInstanceResponseDto>>(
        `/approvals/${selectedApproval.id}/action`,
        dto
      );

      if (res.data) {
        // Refresh local list and close modal
        await fetchApprovals();
        setSelectedApproval(null);
        setDecisionAction(null);
      }
    } catch (err: any) {
      console.error('Decision execution error:', err);
      if (err?.code === 'CONCURRENT_MODIFICATION' || err?.code === 'APPROVAL_CONFLICT' || err?.code === 'CONFLICT') {
        setDecisionError('This approval has already been resolved or decided by another approver. Refreshing data...');
        await fetchApprovals();
      } else {
        setDecisionError(err?.message || 'Failed to submit decision.');
      }
    } finally {
      setSubmittingDecision(false);
    }
  };

  const filteredApprovals = useMemo(() => {
    return approvals.filter((a) => {
      if (filterTab === 'PENDING' && a.status !== 'PENDING') return false;

      if (!searchQuery.trim()) return true;
      const q = searchQuery.toLowerCase();
      return (
        (a.requestNumber || '').toLowerCase().includes(q) ||
        (a.requestTitle || '').toLowerCase().includes(q) ||
        (a.requesterName || '').toLowerCase().includes(q) ||
        (a.departmentName || '').toLowerCase().includes(q) ||
        (a.approverRoleName || '').toLowerCase().includes(q)
      );
    });
  }, [approvals, filterTab, searchQuery]);

  return (
    <div className="space-y-6">
      <SEO title="Approvals Inbox" noIndex={true} />
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Approval Inbox</h1>
          <p className="text-sm text-slate-500 mt-1">
            Review and render decisions on multi-tier requests awaiting your authorization.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={fetchApprovals}
            disabled={loading}
            className="flex items-center gap-1.5 px-3 py-2 text-xs font-semibold text-slate-700 bg-white hover:bg-slate-50 border border-slate-200 rounded-lg shadow-sm transition-colors"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="bg-white rounded-xl border border-slate-200 p-3 sm:p-4 shadow-sm flex flex-col md:flex-row gap-3 sm:gap-4 justify-between items-stretch md:items-center">
        <div className="flex items-center gap-2 overflow-x-auto pb-1 md:pb-0 no-scrollbar">
          <button
            onClick={() => setFilterTab('PENDING')}
            className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-colors flex items-center gap-2 whitespace-nowrap ${
              filterTab === 'PENDING'
                ? 'bg-indigo-600 text-white'
                : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
            }`}
          >
            Pending Actions
            <span
              className={`px-1.5 py-0.2 rounded-full text-[10px] font-bold ${
                filterTab === 'PENDING'
                  ? 'bg-indigo-800 text-white'
                  : 'bg-amber-100 text-amber-800 border border-amber-200'
              }`}
            >
              {approvals.filter((a) => a.status === 'PENDING').length}
            </span>
          </button>

          <button
            onClick={() => setFilterTab('ALL')}
            className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-colors whitespace-nowrap ${
              filterTab === 'ALL'
                ? 'bg-indigo-600 text-white'
                : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
            }`}
          >
            All Assigned ({approvals.length})
          </button>
        </div>

        <div className="relative w-full md:w-80">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search by Request #, title, user..."
            className="w-full pl-9 pr-3 py-1.5 text-xs border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-600"
          />
        </div>
      </div>

      {/* Main Approval List */}
      {error && (
        <div className="p-4 bg-rose-50 border border-rose-200 rounded-xl text-rose-800 text-xs flex items-center gap-3">
          <AlertCircle className="h-4 w-4 text-rose-600 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {loading ? (
        <div className="p-12 text-center bg-white rounded-xl border border-slate-200 shadow-sm space-y-3">
          <RefreshCw className="h-6 w-6 text-indigo-600 animate-spin mx-auto" />
          <p className="text-xs font-medium text-slate-600">Loading pending approvals from orchestration engine...</p>
        </div>
      ) : filteredApprovals.length === 0 ? (
        <div className="p-12 text-center bg-white rounded-xl border border-slate-200 shadow-sm space-y-3">
          <div className="h-12 w-12 rounded-full bg-emerald-50 text-emerald-600 flex items-center justify-center mx-auto border border-emerald-200">
            <CheckCircle2 className="h-6 w-6" />
          </div>
          <h3 className="text-sm font-bold text-slate-900">Your Approval Inbox is Clean</h3>
          <p className="text-xs text-slate-500 max-w-sm mx-auto">
            There are currently no pending requests requiring your review. Submissions routed to your manager or role queue will appear here.
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {filteredApprovals.map((item) => {
            const estimatedAmount = item.requestPayload?.estimatedAmount;
            return (
              <div
                key={item.id}
                className="bg-white rounded-xl border border-slate-200 shadow-sm hover:border-slate-300 transition-all p-5 flex flex-col lg:flex-row items-start lg:items-center justify-between gap-4"
              >
                <div className="space-y-2 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-xs font-bold text-indigo-700 bg-indigo-50 border border-indigo-100 px-2 py-0.5 rounded">
                      {item.requestNumber}
                    </span>
                    <span className="text-xs font-medium text-slate-600 bg-slate-100 px-2 py-0.5 rounded">
                      {item.categoryName}
                    </span>
                    <span className="text-[11px] font-semibold text-purple-700 bg-purple-50 border border-purple-100 px-2 py-0.5 rounded flex items-center gap-1">
                      <Layers className="h-3 w-3" />
                      {item.approverType === 'ROLE' ? item.approverRoleName : 'Direct Manager'}
                    </span>
                    {estimatedAmount !== undefined && (
                      <span className="text-xs font-bold text-emerald-700 bg-emerald-50 border border-emerald-100 px-2 py-0.5 rounded flex items-center gap-0.5">
                        <DollarSign className="h-3 w-3" />
                        {Number(estimatedAmount).toLocaleString('en-US', { minimumFractionDigits: 2 })}
                      </span>
                    )}
                  </div>

                  <h3 className="text-base font-semibold text-slate-900 leading-snug">
                    {item.requestTitle}
                  </h3>

                  <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-500">
                    <span className="flex items-center gap-1">
                      <User className="h-3.5 w-3.5 text-slate-400" />
                      Requester: <strong>{item.requesterName}</strong>
                    </span>
                    <span className="flex items-center gap-1">
                      <Building className="h-3.5 w-3.5 text-slate-400" />
                      {item.departmentName || 'General'}
                    </span>
                    <span className="flex items-center gap-1">
                      <Calendar className="h-3.5 w-3.5 text-slate-400" />
                      Received: {new Date(item.createdAt).toLocaleDateString()}
                    </span>
                    {item.dueAt && (
                      <span className="flex items-center gap-1 text-amber-700 font-medium">
                        <Clock className="h-3.5 w-3.5 text-amber-600" />
                        SLA Due: {new Date(item.dueAt).toLocaleDateString()}
                      </span>
                    )}
                  </div>
                </div>

                <div className="flex flex-wrap items-center gap-2 w-full lg:w-auto justify-start sm:justify-end border-t lg:border-t-0 pt-3 lg:pt-0">
                  <button
                    onClick={() => handleOpenDecision(item)}
                    className="px-3 py-1.5 text-xs font-semibold text-slate-700 bg-slate-50 hover:bg-slate-100 border border-slate-200 rounded-lg transition-colors flex items-center gap-1.5"
                  >
                    Inspect
                    <ChevronRight className="h-3.5 w-3.5 text-slate-400" />
                  </button>

                  <button
                    onClick={() => handleOpenDecision(item, 'REQUEST_INFORMATION')}
                    className="px-3 py-1.5 text-xs font-semibold text-amber-800 bg-amber-50 hover:bg-amber-100 border border-amber-200 rounded-lg transition-colors flex items-center gap-1.5"
                  >
                    <HelpCircle className="h-3.5 w-3.5 text-amber-600" />
                    Request Info
                  </button>

                  <button
                    onClick={() => handleOpenDecision(item, 'REJECT')}
                    className="px-3 py-1.5 text-xs font-semibold text-rose-700 bg-rose-50 hover:bg-rose-100 border border-rose-200 rounded-lg transition-colors flex items-center gap-1.5"
                  >
                    <XCircle className="h-3.5 w-3.5 text-rose-600" />
                    Reject
                  </button>

                  <button
                    onClick={() => handleOpenDecision(item, 'APPROVE')}
                    className="px-3.5 py-1.5 text-xs font-semibold text-white bg-emerald-600 hover:bg-emerald-700 rounded-lg shadow-sm transition-colors flex items-center gap-1.5"
                  >
                    <CheckCircle2 className="h-3.5 w-3.5" />
                    Approve
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Decision Modal / Detailed Inspector */}
      {selectedApproval && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm animate-in fade-in duration-150">
          <div className="bg-white rounded-2xl shadow-xl border border-slate-200 w-full max-w-2xl overflow-hidden flex flex-col max-h-[90vh]">
            {/* Modal Header */}
            <div className="p-6 border-b border-slate-100 flex items-start justify-between bg-slate-50/50">
              <div className="space-y-1">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-bold text-indigo-700 bg-indigo-50 border border-indigo-100 px-2 py-0.5 rounded">
                    {selectedApproval.requestNumber}
                  </span>
                  <span className="text-xs text-slate-500">
                    Category: <strong>{selectedApproval.categoryName}</strong>
                  </span>
                </div>
                <h2 className="text-lg font-bold text-slate-900">{selectedApproval.requestTitle}</h2>
                <p className="text-xs text-slate-500">
                  Submitted by <strong>{selectedApproval.requesterName}</strong> • {selectedApproval.departmentName}
                </p>
              </div>

              <button
                onClick={() => setSelectedApproval(null)}
                className="text-slate-400 hover:text-slate-600 p-1.5 rounded-lg hover:bg-slate-100 transition-colors"
              >
                <XCircle className="h-5 w-5" />
              </button>
            </div>

            {/* Modal Body */}
            <div className="p-6 overflow-y-auto space-y-6">
              {decisionError && (
                <div className="p-3.5 bg-rose-50 border border-rose-200 rounded-xl text-rose-800 text-xs flex items-center gap-2.5">
                  <AlertCircle className="h-4 w-4 text-rose-600 shrink-0" />
                  <span>{decisionError}</span>
                </div>
              )}

              {/* Dynamic Payload Form Attributes */}
              <div className="space-y-3">
                <h4 className="text-xs font-bold text-slate-700 uppercase tracking-wider">
                  Request Specifications &amp; Dynamic Form Data
                </h4>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 bg-slate-50 border border-slate-200 rounded-xl p-4 text-xs">
                  {Object.entries(selectedApproval.requestPayload || {}).map(([k, v]) => (
                    <div key={k} className="space-y-1">
                      <span className="font-medium text-slate-500 capitalize">
                        {k.replace(/([A-Z])/g, ' $1')}
                      </span>
                      <p className="font-semibold text-slate-800 break-words">
                        {typeof v === 'object' ? JSON.stringify(v) : String(v)}
                      </p>
                    </div>
                  ))}
                  {Object.keys(selectedApproval.requestPayload || {}).length === 0 && (
                    <p className="text-slate-400 italic col-span-2">No payload parameters provided.</p>
                  )}
                </div>
              </div>

              {/* Action Decision Selector or Resolved Status */}
              {selectedApproval.status === 'PENDING' ? (
                <>
                  <div className="space-y-3">
                    <label className="text-xs font-bold text-slate-700 uppercase tracking-wider block">
                      Select Decision Action
                    </label>
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
                      <button
                        type="button"
                        onClick={() => setDecisionAction('APPROVE')}
                        className={`p-3 rounded-xl border text-center font-semibold text-xs transition-all flex flex-col items-center gap-1.5 ${
                          decisionAction === 'APPROVE'
                            ? 'border-emerald-500 bg-emerald-50 text-emerald-800 ring-2 ring-emerald-500/20'
                            : 'border-slate-200 bg-white hover:bg-slate-50 text-slate-700'
                        }`}
                      >
                        <CheckCircle2 className="h-4 w-4 text-emerald-600" />
                        Authorize &amp; Approve
                      </button>

                      <button
                        type="button"
                        onClick={() => setDecisionAction('REJECT')}
                        className={`p-3 rounded-xl border text-center font-semibold text-xs transition-all flex flex-col items-center gap-1.5 ${
                          decisionAction === 'REJECT'
                            ? 'border-rose-500 bg-rose-50 text-rose-800 ring-2 ring-rose-500/20'
                            : 'border-slate-200 bg-white hover:bg-slate-50 text-slate-700'
                        }`}
                      >
                        <XCircle className="h-4 w-4 text-rose-600" />
                        Reject Request
                      </button>

                      <button
                        type="button"
                        onClick={() => setDecisionAction('REQUEST_INFORMATION')}
                        className={`p-3 rounded-xl border text-center font-semibold text-xs transition-all flex flex-col items-center gap-1.5 ${
                          decisionAction === 'REQUEST_INFORMATION'
                            ? 'border-amber-500 bg-amber-50 text-amber-800 ring-2 ring-amber-500/20'
                            : 'border-slate-200 bg-white hover:bg-slate-50 text-slate-700'
                        }`}
                      >
                        <HelpCircle className="h-4 w-4 text-amber-600" />
                        Request Info
                      </button>
                    </div>
                  </div>

                  {/* Comments Textarea */}
                  <div className="space-y-2">
                    <label className="text-xs font-bold text-slate-700 block">
                      Decision Notes &amp; Audit Comments
                      {decisionAction === 'REJECT' && (
                        <span className="text-rose-600 ml-1 font-semibold">* (Mandatory for rejection)</span>
                      )}
                    </label>
                    <textarea
                      rows={3}
                      value={comments}
                      onChange={(e) => setComments(e.target.value)}
                      placeholder={
                        decisionAction === 'REJECT'
                          ? 'Please explain the specific reason for rejecting this requisition...'
                          : 'Add any operational guidance, budget authorization codes, or delivery notes...'
                      }
                      className="w-full p-3 text-xs border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-600 placeholder:text-slate-400"
                    />
                  </div>
                </>
              ) : (
                <div className="p-4 bg-emerald-50/60 border border-emerald-200 rounded-xl flex items-center gap-3 text-xs text-emerald-900">
                  <CheckCircle2 className="h-5 w-5 text-emerald-600 shrink-0" />
                  <div>
                    <span className="font-semibold text-slate-900">Decision Already Finalized</span>
                    <p className="text-slate-600 mt-0.5">
                      This approval step was completed with status <span className="font-bold uppercase text-emerald-700">{selectedApproval.status}</span>.
                    </p>
                  </div>
                </div>
              )}

              {/* Historical Actions / Audit Trail */}
              {selectedApproval.actions && selectedApproval.actions.length > 0 && (
                <div className="space-y-2 border-t border-slate-100 pt-4">
                  <h4 className="text-xs font-bold text-slate-700 uppercase tracking-wider">
                    Previous Actions on this Node
                  </h4>
                  <div className="space-y-2">
                    {selectedApproval.actions.map((act) => (
                      <div
                        key={act.id}
                        className="p-3 bg-slate-50 rounded-xl border border-slate-200 flex items-start justify-between text-xs"
                      >
                        <div className="space-y-0.5">
                          <span className="font-semibold text-slate-900">{act.actorName}</span>
                          <span className="ml-2 font-bold text-[10px] uppercase text-indigo-700 bg-indigo-50 px-1.5 py-0.2 rounded">
                            {act.action}
                          </span>
                          {act.comments && <p className="text-slate-600 mt-1 italic">"{act.comments}"</p>}
                        </div>
                        <span className="text-[10px] text-slate-400">
                          {new Date(act.createdAt).toLocaleString()}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>

            {/* Modal Footer */}
            <div className="p-4 border-t border-slate-100 bg-slate-50 flex items-center justify-end gap-2">
              <button
                type="button"
                onClick={() => setSelectedApproval(null)}
                className="px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-200 rounded-xl transition-colors"
              >
                {selectedApproval.status === 'PENDING' ? 'Cancel' : 'Close'}
              </button>

              {selectedApproval.status === 'PENDING' && (
                <button
                  type="button"
                  disabled={!decisionAction || submittingDecision}
                  onClick={() => decisionAction && handleExecuteDecision(decisionAction)}
                  className={`px-5 py-2 text-xs font-semibold text-white rounded-xl shadow-sm transition-all flex items-center gap-2 ${
                    !decisionAction
                      ? 'bg-slate-300 cursor-not-allowed'
                      : decisionAction === 'APPROVE'
                      ? 'bg-emerald-600 hover:bg-emerald-700'
                      : decisionAction === 'REJECT'
                      ? 'bg-rose-600 hover:bg-rose-700'
                      : 'bg-amber-600 hover:bg-amber-700'
                  }`}
                >
                  {submittingDecision ? (
                    <>
                      <RefreshCw className="h-3.5 w-3.5 animate-spin" />
                      Executing Decision...
                    </>
                  ) : (
                    <>
                      <Send className="h-3.5 w-3.5" />
                      Confirm {decisionAction || 'Decision'}
                    </>
                  )}
                </button>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
