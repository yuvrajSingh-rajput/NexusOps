import React from 'react';
import { Plus, GitFork } from 'lucide-react';

export const WorkflowsPage: React.FC = () => {
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Workflow Engine Studio</h1>
          <p className="text-sm text-slate-500 mt-1">Design, version, and publish graph-based operational approval workflows.</p>
        </div>
        <button className="flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-semibold rounded-lg shadow-sm transition-colors">
          <Plus className="h-4 w-4" />
          <span>New Workflow</span>
        </button>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 overflow-hidden shadow-sm">
        <div className="p-4 border-b border-slate-200 bg-slate-50/50 flex items-center justify-between">
          <span className="text-xs font-semibold text-slate-700 uppercase tracking-wider">Published Workflows</span>
          <span className="text-xs text-indigo-700 bg-indigo-50 border border-indigo-200 px-2 py-0.5 rounded-full font-medium">
            1 Active Definition
          </span>
        </div>

        <div className="p-6">
          <div className="flex items-center justify-between">
            <div className="flex items-start gap-3">
              <div className="p-2.5 bg-indigo-50 text-indigo-600 rounded-lg">
                <GitFork className="h-5 w-5" />
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <h4 className="text-sm font-semibold text-slate-900">Standard Hardware Purchase Workflow</h4>
                  <span className="text-xs bg-emerald-100 text-emerald-800 font-bold px-2 py-0.5 rounded-full">v1.0 (PUBLISHED)</span>
                </div>
                <p className="text-xs text-slate-500 mt-0.5">
                  Route: Start → Manager Approval → Condition (&gt;$1,000 Finance Approval) → IT Task → End
                </p>
              </div>
            </div>
            <button className="px-3 py-1.5 text-xs font-semibold text-indigo-600 hover:bg-indigo-50 border border-indigo-200 rounded-lg transition-colors">
              Open in Canvas
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

