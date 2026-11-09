import React from 'react';
import { Upload, FileText, CheckCircle2 } from 'lucide-react';

export const KnowledgePage: React.FC = () => {
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Organization Knowledge Base</h1>
          <p className="text-sm text-slate-500 mt-1">Upload company policy manuals for tenant-scoped Nexus AI RAG grounding.</p>
        </div>
        <button className="flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-semibold rounded-lg shadow-sm transition-colors">
          <Upload className="h-4 w-4" />
          <span>Upload Policy Document</span>
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm flex items-start justify-between">
          <div className="flex items-start gap-3">
            <div className="p-2.5 bg-indigo-50 text-indigo-600 rounded-lg">
              <FileText className="h-5 w-5" />
            </div>
            <div>
              <h4 className="text-sm font-semibold text-slate-900">IT Equipment &amp; Laptop Policy 2026.pdf</h4>
              <p className="text-xs text-slate-500 mt-0.5">34 chunks indexed in pgvector • 768 dimensions</p>
              <div className="flex items-center gap-1.5 mt-2 text-xs font-semibold text-emerald-600">
                <CheckCircle2 className="h-3.5 w-3.5" />
                <span>INDEXED &amp; ACTIVE</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

