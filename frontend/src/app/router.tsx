import { createBrowserRouter, Navigate } from 'react-router-dom';
import { ProtectedRoute } from '@/auth/ProtectedRoute';
import { AppShell } from '@/components/layout/AppShell';
import { LoginPage } from '@/pages/LoginPage';
import { NotFoundPage } from '@/pages/NotFoundPage';
import { SystemStatusPage } from '@/pages/SystemStatusPage';
import { EmployeeDashboard } from '@/pages/employee/EmployeeDashboard';
import { CreateIncidentPage } from '@/pages/employee/CreateIncidentPage';
import { AgentDashboard } from '@/pages/agent/AgentDashboard';
import { AgentOverview } from '@/pages/agent/AgentOverview';
import { AdminDashboard } from '@/pages/admin/AdminDashboard';
import { AdminUsersPage } from '@/pages/admin/AdminUsersPage';
import { AdminTeamsPage } from '@/pages/admin/AdminTeamsPage';
import { AdminSlaPage } from '@/pages/admin/AdminSlaPage';
import { AdminReportsPage } from '@/pages/admin/AdminReportsPage';
import { AdminIncidentsPage } from '@/pages/admin/AdminIncidentsPage';
import { IncidentDetailPage } from '@/pages/IncidentDetailPage';
import { RoleHome } from '@/app/RoleHome';

/**
 * Every application route sits behind ProtectedRoute, which also decides the
 * landing screen per role. The guard is for usability; the API enforces the
 * same rules independently.
 */
export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/', element: <RoleHome /> },

  {
    // The detail page is shared: all three roles reach it, and the API decides
    // which incidents each of them may actually open.
    element: (
      <ProtectedRoute allow={['EMPLOYEE', 'SUPPORT_AGENT', 'ADMIN']}>
        <AppShell />
      </ProtectedRoute>
    ),
    children: [{ path: '/incidents/:id', element: <IncidentDetailPage /> }],
  },
  {
    element: (
      <ProtectedRoute allow={['EMPLOYEE']}>
        <AppShell />
      </ProtectedRoute>
    ),
    children: [
      { path: '/employee', element: <EmployeeDashboard /> },
      { path: '/employee/new', element: <CreateIncidentPage /> },
    ],
  },
  {
    element: (
      <ProtectedRoute allow={['SUPPORT_AGENT']}>
        <AppShell />
      </ProtectedRoute>
    ),
    children: [
      { path: '/agent', element: <AgentOverview /> },
      { path: '/agent/queue', element: <AgentDashboard /> },
    ],
  },
  {
    element: (
      <ProtectedRoute allow={['ADMIN']}>
        <AppShell />
      </ProtectedRoute>
    ),
    children: [
      { path: '/admin', element: <AdminDashboard /> },
      { path: '/admin/incidents', element: <AdminIncidentsPage /> },
      { path: '/admin/users', element: <AdminUsersPage /> },
      { path: '/admin/teams', element: <AdminTeamsPage /> },
      { path: '/admin/sla-policies', element: <AdminSlaPage /> },
      // The old path stays reachable so an existing bookmark does not 404.
      { path: '/admin/sla', element: <Navigate to="/admin/sla-policies" replace /> },
      { path: '/admin/reports', element: <AdminReportsPage /> },
    ],
  },

  { path: '/system', element: <SystemStatusPage /> },
  { path: '/404', element: <NotFoundPage /> },
  { path: '*', element: <Navigate to="/404" replace /> },
]);
