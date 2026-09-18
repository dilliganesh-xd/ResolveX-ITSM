import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, setAuthToken } from '@/api/client';
import {
  AuthContext,
  clearStoredSession,
  readStoredSession,
  writeStoredSession,
  type AuthContextValue,
  type StoredSession,
} from './context';
import type { AuthUser, LoginResponse } from '@/lib/types';

/**
 * Holds the session for the app.
 *
 * <p>The token lives in web storage - localStorage when the user asked to be
 * remembered, sessionStorage when they did not. That is the pragmatic MVP
 * choice - it survives a reload and needs no refresh-token plumbing - but it
 * is readable by any script on the origin, so it is XSS-exposed. Moving to an
 * httpOnly cookie plus a CSRF token is the intended hardening step before
 * production.
 *
 * <p>This file exports only the component. The context itself lives in
 * context.ts so Fast Refresh has a clean component boundary and cannot end up
 * with two copies of the context object.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [initialising, setInitialising] = useState(true);

  const logout = useCallback(() => {
    clearStoredSession();
    setAuthToken(null);
    setUser(null);
  }, []);

  // Restore a stored session, then confirm it with the API. A token the server
  // no longer accepts must not leave the app looking logged in.
  useEffect(() => {
    const stored = readStoredSession();
    if (!stored) {
      setInitialising(false);
      return;
    }

    setAuthToken(stored.token);
    let cancelled = false;

    api
      .get('/auth/me')
      .then(() => {
        if (!cancelled) {
          setUser(stored.user);
        }
      })
      .catch(() => {
        if (!cancelled) {
          logout();
        }
      })
      .finally(() => {
        if (!cancelled) {
          setInitialising(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [logout]);

  const login = useCallback(
    async (email: string, password: string, remember: boolean): Promise<AuthUser> => {
      const { data } = await api.post<LoginResponse>('/auth/login', { email, password });
      const session: StoredSession = {
        token: data.token,
        user: {
          userId: data.userId,
          name: data.name,
          email: data.email,
          role: data.role,
          team: data.team,
        },
      };
      writeStoredSession(session, remember);
      setAuthToken(session.token);
      setUser(session.user);
      return session.user;
    },
    [],
  );

  const value = useMemo<AuthContextValue>(
    () => ({ user, initialising, login, logout }),
    [user, initialising, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
