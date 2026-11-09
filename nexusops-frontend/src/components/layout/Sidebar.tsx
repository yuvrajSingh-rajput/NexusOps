import React, { useMemo } from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  FileText,
  CheckSquare,
  ListTodo,
  BookOpen,
  GitFork,
  BarChart3,
  Building2,
  Users,
  Sparkles,
  History,
} from 'lucide-react';
import { useAuthStore } from '@/features/auth/authStore';
import { UserMeResponseDto } from '@/types/api';

interface NavItem {
  name: string;
  to: string;
  icon: React.ElementType;
  hasPermission?: (user: UserMeResponseDto | null) => boolean;
}

const hasAny = (user: UserMeResponseDto | null, permissions: string[], roles: string[] = []): boolean => {
  if (!user) return false;
  if (user.roles?.includes('ORG_ADMIN')) return true;
  const matchPerm = permissions.some((p) => user.permissions?.includes(p));
  const matchRole = roles.some((r) => user.roles?.includes(r));
  return matchPerm || matchRole;
};

const operationsNav: NavItem[] = [
  { name: 'Dashboard', to: '/', icon: LayoutDashboard },
  { name: 'My Requests', to: '/requests', icon: FileText },
  {
    name: 'Approvals',
    to: '/approvals',
    icon: CheckSquare,
    hasPermission: (u) => hasAny(u, ['APPROVAL_VIEW_PENDING', 'APPROVAL_EXECUTE'], ['MANAGER', 'FINANCE']),
  },
  {
    name: 'Tasks & Queues',
    to: '/tasks',
    icon: ListTodo,
    hasPermission: (u) => hasAny(u, ['TASK_VIEW_ASSIGNED', 'TASK_QUEUE_VIEW', 'TASK_CLAIM'], ['OPERATOR']),
  },
  {
    name: 'Knowledge Base',
    to: '/knowledge',
    icon: BookOpen,
    hasPermission: (u) => hasAny(u, ['KNOWLEDGE_VIEW', 'KNOWLEDGE_MANAGE']),
  },
  {
    name: 'Workflow Studio',
    to: '/workflows',
    icon: GitFork,
    hasPermission: (u) => hasAny(u, ['WORKFLOW_VIEW', 'WORKFLOW_MANAGE']),
  },
  { name: 'Nexus AI Co-pilot', to: '/ai-assistant', icon: Sparkles },
  {
    name: 'Audit Trail',
    to: '/audit',
    icon: History,
    hasPermission: (u) => hasAny(u, ['AUDIT_VIEW']),
  },
  {
    name: 'Analytics',
    to: '/analytics',
    icon: BarChart3,
    hasPermission: (u) => hasAny(u, ['ANALYTICS_VIEW'], ['MANAGER', 'FINANCE']),
  },
];

const orgNav: NavItem[] = [
  {
    name: 'Departments',
    to: '/departments',
    icon: Building2,
  },
  {
    name: 'Employees & Directory',
    to: '/employees',
    icon: Users,
  },
];

export const Sidebar: React.FC = () => {
  const user = useAuthStore((s) => s.user);
  const currentOrg = user?.availableOrganizations?.find((o) => o.isCurrent);

  const visibleOperations = useMemo(() => {
    return operationsNav.filter((item) => (item.hasPermission ? item.hasPermission(user) : true));
  }, [user]);

  const visibleOrgNav = useMemo(() => {
    return orgNav.filter((item) => (item.hasPermission ? item.hasPermission(user) : true));
  }, [user]);

  return (
    <aside className="w-64 bg-slate-900 text-slate-300 flex flex-col shrink-0 border-r border-slate-800 select-none">
      {/* Brand Header */}
      <div className="h-16 flex items-center px-6 gap-3 border-b border-slate-800">
        <div className="h-9 w-9 rounded-lg bg-indigo-600 flex items-center justify-center text-white shadow-md shadow-indigo-500/20">
          <Sparkles className="h-5 w-5" />
        </div>
        <div>
          <span className="text-base font-bold text-white tracking-tight">NexusOps</span>
          <span className="block text-xs text-indigo-400 font-medium">Enterprise Portal</span>
        </div>
      </div>

      {/* Navigation Links */}
      <nav className="flex-1 px-3 py-4 space-y-4 overflow-y-auto">
        {/* Operations */}
        {visibleOperations.length > 0 && (
          <div>
            <div className="px-3 pb-2 text-[11px] font-semibold tracking-wider text-slate-400 uppercase">
              Operations
            </div>
            <div className="space-y-1">
              {visibleOperations.map((item) => {
                const Icon = item.icon;
                return (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    end={item.to === '/'}
                    className={({ isActive }) =>
                      `flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                        isActive
                          ? 'bg-indigo-600 text-white shadow-sm'
                          : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
                      }`
                    }
                  >
                    <Icon className="h-4 w-4 shrink-0" />
                    <span>{item.name}</span>
                  </NavLink>
                );
              })}
            </div>
          </div>
        )}

        {/* Organization Administration */}
        {visibleOrgNav.length > 0 && (
          <div>
            <div className="px-3 pb-2 text-[11px] font-semibold tracking-wider text-slate-400 uppercase">
              Organization
            </div>
            <div className="space-y-1">
              {visibleOrgNav.map((item) => {
                const Icon = item.icon;
                return (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    className={({ isActive }) =>
                      `flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                        isActive
                          ? 'bg-indigo-600 text-white shadow-sm'
                          : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
                      }`
                    }
                  >
                    <Icon className="h-4 w-4 shrink-0" />
                    <span>{item.name}</span>
                  </NavLink>
                );
              })}
            </div>
          </div>
        )}
      </nav>

      {/* Tenant Indicator Footer */}
      <div className="p-4 border-t border-slate-800 bg-slate-950/40">
        <div className="text-[11px] text-slate-400 uppercase tracking-wider font-semibold">Active Tenant</div>
        <div className="text-sm font-bold text-white truncate mt-0.5">
          {currentOrg?.organizationName || 'Default Organization'}
        </div>
        <div className="text-[11px] text-emerald-400 flex items-center gap-1.5 mt-1">
          <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 inline-block animate-pulse"></span>
          Tenant Isolated (RLS)
        </div>
      </div>
    </aside>
  );
};
