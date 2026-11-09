import { createBrowserRouter } from 'react-router-dom';
import { MainLayout } from '@/components/layout/MainLayout';
import { DashboardPage } from '@/features/dashboard/DashboardPage';
import { RequestsPage } from '@/features/requests/RequestsPage';
import { ApprovalsPage } from '@/features/approvals/ApprovalsPage';
import { TasksPage } from '@/features/tasks/TasksPage';
import { KnowledgePage } from '@/features/knowledge/KnowledgePage';
import { WorkflowsPage } from '@/features/workflows/WorkflowsPage';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <MainLayout />,
    children: [
      { index: true, element: <DashboardPage /> },
      { path: 'requests', element: <RequestsPage /> },
      { path: 'approvals', element: <ApprovalsPage /> },
      { path: 'tasks', element: <TasksPage /> },
      { path: 'knowledge', element: <KnowledgePage /> },
      { path: 'workflows', element: <WorkflowsPage /> },
      { path: 'analytics', element: <DashboardPage /> },
      { path: 'settings', element: <div className="p-4 text-sm text-slate-500">Settings panel (Phase 2 &amp; 10)</div> },
    ],
  },
]);
