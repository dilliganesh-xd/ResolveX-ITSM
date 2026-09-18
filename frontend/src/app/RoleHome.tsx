import { Navigate } from 'react-router-dom';
import { homeRouteForRole } from '@/auth/context';
import { useAuth } from '@/auth/useAuth';
import { color } from '@/lib/tokens';

/** Sends "/" to the right dashboard once the session is known. */
export function RoleHome() {
  const { user, initialising } = useAuth();

  if (initialising) {
    return (
      <div style={{ padding: 40, color: color.muted, fontSize: 13, fontWeight: 600 }}>Restoring session...</div>
    );
  }
  return <Navigate to={user ? homeRouteForRole(user.role) : '/login'} replace />;
}
