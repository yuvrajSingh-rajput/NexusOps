import React from 'react';
import { Check, X, HelpCircle } from 'lucide-react';

export const ApprovalsPage: React.FC = () => {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Approval Inbox</h1>
        <p className="text-sm text-slate-500 mt-1">Review and render decisions on pending requests awaiting your authorization.</p>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 overflow-hidden shadow-sm">
        <div className="p-4 border-b border-slate-200 bg-slate-50/50 flex items-center justify-between">
          <span className="text-xs font-semibold text-slate-700 uppercase tracking-wider">Pending Decisions</span>
          <span className="text-xs text-amber-700 bg-amber-50 border border-amber-200 px-2 py-0.5 rounded-full font-medium">
            1 Action Required
          </span>
        </div>

        <div className="p-6 divide-y divide-slate-100">
          <div className="flex items-start justify-between py-4 first:pt-0 last:pb-0">
            <div className="space-y-1">
              <div className="flex items-center gap-2">
                <span className="text-xs font-bold text-indigo-600 bg-indigo-50 px-2 py-0.5 rounded">
                  REQ-1001
                </span>
                <h3 className="text-base font-semibold text-slate-900">MacBook Pro 16 M3 Max ($2,400)</h3>
              </div>
              <p className="text-xs text-slate-500">
                Requester: <strong>Alex Rivera</strong> (Engineering) • Threshold requires Manager &amp; Finance approval
              </p>
            </div>

            <div className="flex items-center gap-2">
              <button className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-100 border border-slate-200 rounded-lg transition-colors">
                <HelpCircle className="h-3.5 w-3.5 text-slate-500" />
                Request Info
              </button>
              <button className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-rose-700 hover:bg-rose-50 border border-rose-200 rounded-lg transition-colors">
                <X className="h-3.5 w-3.5 text-rose-600" />
                Reject
              </button>
              <button className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-white bg-emerald-600 hover:bg-emerald-700 rounded-lg shadow-sm transition-colors">
                <Check className="h-3.5 w-3.5" />
                Approve
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
