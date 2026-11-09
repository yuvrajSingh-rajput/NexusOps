import React from 'react';
import { Search, Bell, Sparkles, User } from 'lucide-react';

interface TopbarProps {
  onOpenAiChat?: () => void;
}

export const Topbar: React.FC<TopbarProps> = ({ onOpenAiChat }) => {
  return (
    <header className="h-16 bg-white border-b border-slate-200 px-6 flex items-center justify-between shrink-0">
      {/* Search Input */}
      <div className="relative w-80">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
        <input
          type="text"
          placeholder="Search requests, tasks, policies..."
          className="w-full pl-9 pr-4 py-1.5 text-sm bg-slate-50 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all placeholder:text-slate-400"
        />
      </div>

      {/* Action Controls */}
      <div className="flex items-center gap-3">
        {/* Nexus AI Assistant Trigger */}
        <button
          onClick={onOpenAiChat}
          className="flex items-center gap-2 px-3.5 py-1.5 bg-gradient-to-r from-indigo-50 to-purple-50 text-indigo-700 hover:from-indigo-100 hover:to-purple-100 border border-indigo-200/80 rounded-lg text-sm font-medium transition-all shadow-sm"
          title="Open Nexus AI Co-pilot"
        >
          <Sparkles className="h-4 w-4 text-indigo-600 animate-spin-slow" />
          <span>Nexus AI</span>
          <span className="text-[10px] bg-indigo-200/80 text-indigo-800 px-1.5 py-0.5 rounded font-bold">
            PRO
          </span>
        </button>

        {/* Notifications */}
        <button className="relative p-2 text-slate-500 hover:text-slate-700 hover:bg-slate-100 rounded-lg transition-colors">
          <Bell className="h-5 w-5" />
          <span className="absolute top-1.5 right-1.5 h-2 w-2 rounded-full bg-rose-500 ring-2 ring-white"></span>
        </button>

        {/* User Profile */}
        <div className="flex items-center gap-3 pl-2 border-l border-slate-200">
          <div className="h-8 w-8 rounded-full bg-indigo-100 text-indigo-700 flex items-center justify-center font-semibold text-xs border border-indigo-200">
            <User className="h-4 w-4" />
          </div>
          <div className="hidden md:block text-left">
            <div className="text-xs font-semibold text-slate-800 leading-tight">Alex Rivera</div>
            <div className="text-[11px] text-slate-500">Software Engineer</div>
          </div>
        </div>
      </div>
    </header>
  );
};
