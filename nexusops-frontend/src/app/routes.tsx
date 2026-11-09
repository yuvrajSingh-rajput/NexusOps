import { createBrowserRouter, RouteObject } from 'react-router-dom';
import { MainLayout } from '@/components/layout/MainLayout';
import { ProtectedRoute } from '@/components/auth/ProtectedRoute';
import { LoginPage } from '@/features/auth/LoginPage';
import { RegisterOrgPage } from '@/features/auth/RegisterOrgPage';
import { DashboardPage } from '@/features/dashboard/DashboardPage';
import { RequestsPage } from '@/features/requests/RequestsPage';
import { ApprovalsPage } from '@/features/approvals/ApprovalsPage';
import { TasksPage } from '@/features/tasks/TasksPage';
import { KnowledgePage } from '@/features/knowledge/KnowledgePage';
import { WorkflowsPage } from '@/features/workflows/WorkflowsPage';
import { DepartmentsPage } from '@/features/organization/DepartmentsPage';
import { EmployeesPage } from '@/features/organization/EmployeesPage';
import { AiAssistantPage } from '@/features/ai-assistant/AiAssistantPage';
import { AuditTrailPage } from '@/features/audit/AuditTrailPage';

export const routes: RouteObject[] = [
  // Public Authentication Routes
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/register',
    element: <RegisterOrgPage />,
  },

  // Protected Enterprise Workspace Routes
  {
    path: '/',
    element: <ProtectedRoute />,
    children: [
      {
        element: <MainLayout />,
        children: [
          { index: true, element: <DashboardPage /> },
          { path: 'requests', element: <RequestsPage /> },
          { path: 'approvals', element: <ApprovalsPage /> },
          { path: 'tasks', element: <TasksPage /> },
          { path: 'knowledge', element: <KnowledgePage /> },
          { path: 'workflows', element: <WorkflowsPage /> },
          { path: 'ai-assistant', element: <AiAssistantPage /> },
          { path: 'audit', element: <AuditTrailPage /> },
          { path: 'departments', element: <DepartmentsPage /> },
          { path: 'employees', element: <EmployeesPage /> },
          { path: 'analytics', element: <DashboardPage /> },
          { path: 'settings', element: <div className="p-4 text-sm text-slate-500">Settings panel (Phase 2 &amp; 10)</div> },
        ],
      },
    ],
  },
];

export const router = createBrowserRouter(routes);
