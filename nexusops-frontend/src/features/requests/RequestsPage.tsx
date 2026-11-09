import React from 'react';
import { Plus, Filter } from 'lucide-react';

export const RequestsPage: React.FC = () => {
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">My Requests</h1>
          <p className="text-sm text-slate-500 mt-1">Submit, track, and manage your internal operational requests.</p>
        </div>
        <button className="flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-semibold rounded-lg shadow-sm transition-colors">
          <Plus className="h-4 w-4" />
          <span>New Request</span>
        </button>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 overflow-hidden shadow-sm">
        <div className="p-4 border-b border-slate-200 flex items-center justify-between bg-slate-50/50">
          <div className="flex items-center gap-2 text-xs font-medium text-slate-600">
            <Filter className="h-3.5 w-3.5 text-slate-400" />
            <span>Filter by Category &amp; Status</span>
          </div>
          <div className="text-xs text-slate-500 font-medium">1 Request Total</div>
        </div>

        <table className="w-full text-left text-sm text-slate-600">
          <thead className="bg-slate-50 text-xs font-semibold uppercase tracking-wider text-slate-500 border-b border-slate-200">
            <tr>
              <th className="px-6 py-3">Number</th>
              <th className="px-6 py-3">Category</th>
              <th className="px-6 py-3">Title</th>
              <th className="px-6 py-3">Status</th>
              <th className="px-6 py-3">Submitted</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-200">
            <tr className="hover:bg-slate-50 transition-colors">
              <td className="px-6 py-4 font-semibold text-indigo-600">REQ-1001</td>
              <td className="px-6 py-4">Hardware Purchase</td>
              <td className="px-6 py-4 font-medium text-slate-900">MacBook Pro 16 M3 Max ($2,400)</td>
              <td className="px-6 py-4">
                <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-amber-100 text-amber-800">
                  IN_REVIEW
                </span>
              </td>
              <td className="px-6 py-4 text-xs text-slate-500">Today, 10:15 AM</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
};
