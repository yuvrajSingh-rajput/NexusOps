import React, { useState } from 'react';
import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';
import { Sparkles, X, Send } from 'lucide-react';

export const MainLayout: React.FC = () => {
  const [aiChatOpen, setAiChatOpen] = useState(false);
  const [inputPrompt, setInputPrompt] = useState('');

  return (
    <div className="flex h-screen w-screen overflow-hidden bg-slate-50">
      {/* Left Sidebar */}
      <Sidebar />

      {/* Main Workspace Area */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        <Topbar onOpenAiChat={() => setAiChatOpen(!aiChatOpen)} />

        <main className="flex-1 overflow-y-auto p-6">
          <div className="max-w-7xl mx-auto">
            <Outlet />
          </div>
        </main>
      </div>

      {/* Nexus AI Assistant Slide-over Drawer */}
      {aiChatOpen && (
        <aside className="w-96 bg-white border-l border-slate-200 flex flex-col shadow-xl z-20 transition-all duration-300">
          <div className="h-16 px-4 border-b border-slate-200 flex items-center justify-between bg-slate-50">
            <div className="flex items-center gap-2">
              <div className="p-1.5 bg-indigo-600 rounded-md text-white">
                <Sparkles className="h-4 w-4" />
              </div>
              <div>
                <h3 className="text-sm font-semibold text-slate-900">Nexus AI Operations Co-pilot</h3>
                <p className="text-[11px] text-slate-500">Grounded in Acme Policy Docs</p>
              </div>
            </div>
            <button
              onClick={() => setAiChatOpen(false)}
              className="p-1.5 text-slate-400 hover:text-slate-600 rounded-lg hover:bg-slate-200/60"
            >
              <X className="h-4 w-4" />
            </button>
          </div>

          <div className="flex-1 p-4 overflow-y-auto space-y-3">
            <div className="bg-indigo-50/70 border border-indigo-100 rounded-lg p-3 text-xs text-indigo-900">
              👋 <strong>Hi Alex!</strong> I can answer policy questions from company documents (with citations) or help prepare operational requests and approvals.
            </div>
            <div className="text-[11px] text-slate-400 uppercase tracking-wider font-semibold px-1">
              Suggested Queries
            </div>
            <div className="space-y-1.5">
              <button
                onClick={() => setInputPrompt("What is our developer laptop budget?")}
                className="w-full text-left p-2 text-xs bg-slate-50 hover:bg-slate-100 border border-slate-200 rounded-md text-slate-700 transition-colors"
              >
                💻 "What is our developer laptop budget?"
              </button>
              <button
                onClick={() => setInputPrompt("Show my pending approval requests")}
                className="w-full text-left p-2 text-xs bg-slate-50 hover:bg-slate-100 border border-slate-200 rounded-md text-slate-700 transition-colors"
              >
                📋 "Show my pending approval requests"
              </button>
            </div>
          </div>

          <div className="p-3 border-t border-slate-200 bg-white">
            <div className="flex items-center gap-2">
              <input
                type="text"
                value={inputPrompt}
                onChange={(e) => setInputPrompt(e.target.value)}
                placeholder="Ask Nexus AI..."
                className="flex-1 px-3 py-2 text-xs bg-slate-50 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
              />
              <button className="p-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg shadow-sm">
                <Send className="h-3.5 w-3.5" />
              </button>
            </div>
          </div>
        </aside>
      )}
    </div>
  );
};
