import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth, } from './useAuth';
import { homeRouteForRole } from './context';
import type { RoleName } from '@/lib/types';
import { color } from '@/lib/tokens';

interface ProtectedRouteProps {
  allow: RoleName[];
  children: ReactNode;
}

/**
 * Route guard. This is a usability boundary, not a security one - the API
 * enforces the same rules, because anything the browser decides can be bypassed.
 */
export function ProtectedRoute({ allow, children }: ProtectedRouteProps) {
  const { user, initialising } = useAuth();
  const location = useLocation();

  if (initialising) {
    return (
      <div style={{ padding: 40, color: color.muted, fontSize: 13, fontWeight: 600 }}>
        Restoring session...
      </div>
    );
  }

  if (!user) {
    // Remember where they were headed so login can send them back.
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  if (!allow.includes(user.role)) {
    return <Navigate to={homeRouteForRole(user.role)} replace />;
  }

  return <>{children}</>;
}
