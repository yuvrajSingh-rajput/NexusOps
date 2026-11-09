import React from 'react';
import { CheckCircle2, UserCheck } from 'lucide-react';

export const TasksPage: React.FC = () => {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Fulfillment Tasks &amp; Queues</h1>
        <p className="text-sm text-slate-500 mt-1">Operational queues for IT, Procurement, and HR fulfillments.</p>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 overflow-hidden shadow-sm">
        <div className="p-4 border-b border-slate-200 bg-slate-50/50 flex items-center justify-between">
          <span className="text-xs font-semibold text-slate-700 uppercase tracking-wider">Queue: IT Procurement</span>
          <span className="text-xs text-indigo-700 bg-indigo-50 border border-indigo-200 px-2 py-0.5 rounded-full font-medium">
            1 Task Ready
          </span>
        </div>

        <div className="p-6">
          <div className="flex items-center justify-between">
            <div className="space-y-1">
              <div className="flex items-center gap-2">
                <span className="text-xs font-bold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded">
                  TASK-1001
                </span>
                <span className="text-xs font-bold text-slate-400">Linked to REQ-1001</span>
                <h4 className="text-sm font-semibold text-slate-900">Procure &amp; Provision MacBook Pro</h4>
              </div>
              <p className="text-xs text-slate-500">
                All required approvals completed. Deliver to Alex Rivera (Engineering).
              </p>
            </div>
            <div className="flex items-center gap-2">
              <button className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-indigo-700 bg-indigo-50 hover:bg-indigo-100 border border-indigo-200 rounded-lg transition-colors">
                <UserCheck className="h-3.5 w-3.5" />
                Claim Task
              </button>
              <button className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-700 rounded-lg shadow-sm transition-colors">
                <CheckCircle2 className="h-3.5 w-3.5" />
                Complete Fulfillment
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
