import React, { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, Bell, Sparkles, LogOut, Building, Check, ChevronDown } from 'lucide-react';
import { useAuthStore } from '@/features/auth/authStore';

interface TopbarProps {
  onOpenAiChat?: () => void;
}

export const Topbar: React.FC<TopbarProps> = ({ onOpenAiChat }) => {
  const navigate = useNavigate();
  const { user, logout, switchOrganization } = useAuthStore();
  const [userDropdownOpen, setUserDropdownOpen] = useState(false);
  const [orgDropdownOpen, setOrgDropdownOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const orgRef = useRef<HTMLDivElement>(null);

  // Close dropdowns on outside click
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setUserDropdownOpen(false);
      }
      if (orgRef.current && !orgRef.current.contains(e.target as Node)) {
        setOrgDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const handleSwitchOrg = async (targetOrgId: string) => {
    setOrgDropdownOpen(false);
    try {
      await switchOrganization(targetOrgId);
    } catch (err) {
      console.error('Failed to switch tenant organization', err);
    }
  };

  const currentOrg = user?.availableOrganizations?.find((o) => o.isCurrent);
  const userInitials = user ? `${user.firstName?.[0] || ''}${user.lastName?.[0] || ''}` : 'U';

  return (
    <header className="h-16 bg-white border-b border-slate-200 px-6 flex items-center justify-between shrink-0">
      {/* Search Input & Tenant Switcher */}
      <div className="flex items-center gap-4">
        {/* Organization Switcher */}
        {user?.availableOrganizations && user.availableOrganizations.length > 0 && (
          <div className="relative" ref={orgRef}>
            <button
              onClick={() => setOrgDropdownOpen(!orgDropdownOpen)}
              className="flex items-center gap-2 px-3 py-1.5 bg-slate-100 hover:bg-slate-200/80 rounded-lg text-xs font-semibold text-slate-800 transition-colors border border-slate-200"
            >
              <Building className="h-3.5 w-3.5 text-indigo-600" />
              <span>{currentOrg?.organizationName || 'Organization'}</span>
              <ChevronDown className="h-3.5 w-3.5 text-slate-400" />
            </button>

            {orgDropdownOpen && (
              <div className="absolute left-0 mt-2 w-64 bg-white rounded-xl shadow-lg border border-slate-200 py-1.5 z-30 animate-in fade-in zoom-in-95 duration-100">
                <div className="px-3 py-1 text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                  Select Organization
                </div>
                {user.availableOrganizations.map((org) => (
                  <button
                    key={org.organizationId}
                    onClick={() => handleSwitchOrg(org.organizationId)}
                    className="w-full text-left px-3 py-2 text-xs flex items-center justify-between hover:bg-slate-50 transition-colors"
                  >
                    <div>
                      <div className="font-semibold text-slate-800">{org.organizationName}</div>
                      <div className="text-[11px] text-slate-400 font-mono">@{org.organizationSlug}</div>
                    </div>
                    {org.isCurrent && <Check className="h-4 w-4 text-indigo-600" />}
                  </button>
                ))}
              </div>
            )}
          </div>
        )}

        <div className="relative w-72">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
          <input
            type="text"
            placeholder="Search requests, tasks, policies..."
            className="w-full pl-9 pr-4 py-1.5 text-sm bg-slate-50 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all placeholder:text-slate-400"
          />
        </div>
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

        {/* User Profile & Menu */}
        <div className="relative" ref={dropdownRef}>
          <button
            onClick={() => setUserDropdownOpen(!userDropdownOpen)}
            className="flex items-center gap-2.5 pl-2 border-l border-slate-200 hover:opacity-90 transition-opacity"
          >
            <div className="h-8 w-8 rounded-full bg-indigo-600 text-white flex items-center justify-center font-bold text-xs shadow-sm">
              {userInitials}
            </div>
            <div className="hidden md:block text-left">
              <div className="text-xs font-semibold text-slate-800 leading-tight">
                {user ? `${user.firstName} ${user.lastName}` : 'Signed In User'}
              </div>
              <div className="text-[11px] text-slate-500">{user?.jobTitle || user?.email || 'Operations'}</div>
            </div>
          </button>

          {userDropdownOpen && (
            <div className="absolute right-0 mt-2 w-56 bg-white rounded-xl shadow-xl border border-slate-200 py-1.5 z-30 animate-in fade-in zoom-in-95 duration-100">
              <div className="px-4 py-2 border-b border-slate-100">
                <p className="text-xs font-semibold text-slate-800">
                  {user ? `${user.firstName} ${user.lastName}` : 'User'}
                </p>
                <p className="text-[11px] text-slate-400 truncate">{user?.email}</p>
                <div className="mt-1 flex flex-wrap gap-1">
                  {user?.roles?.map((r) => (
                    <span
                      key={r}
                      className="inline-block px-1.5 py-0.2 rounded text-[10px] font-semibold bg-indigo-50 text-indigo-700 border border-indigo-200"
                    >
                      {r.replace('ROLE_', '')}
                    </span>
                  ))}
                </div>
              </div>

              <button
                onClick={handleLogout}
                className="w-full text-left px-4 py-2 text-xs text-rose-600 hover:bg-rose-50 flex items-center gap-2 transition-colors font-semibold"
              >
                <LogOut className="h-4 w-4" />
                <span>Sign Out</span>
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
};
