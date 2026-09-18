import { createContext } from 'react';
import type { AuthUser, RoleName } from '@/lib/types';

export interface AuthContextValue {
  user: AuthUser | null;
  /** True until the stored session has been read and validated on first load. */
  initialising: boolean;
  /**
   * `remember` decides whether the session outlives the browser window. See
   * {@link writeStoredSession} for how that is honoured.
   */
  login: (email: string, password: string, remember: boolean) => Promise<AuthUser>;
  logout: () => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export const STORAGE_KEY = 'itsm.auth';

export interface StoredSession {
  token: string;
  user: AuthUser;
}

/**
 * Where the session is kept.
 *
 * <p>"Remember me" is the difference between a session that survives closing
 * the browser and one that does not, so it selects the store rather than
 * setting a flag the app would then have to police itself: the browser empties
 * sessionStorage when the tab closes, which is exactly the unchecked
 * behaviour, enforced by something more reliable than our own code.
 *
 * <p>Reads look in both, because the previous sign-in may have chosen
 * differently. Writes clear both first, so a remembered session cannot survive
 * a later sign-in that asked not to be remembered.
 */
export function readStoredSession(): StoredSession | null {
  for (const store of [window.localStorage, window.sessionStorage]) {
    try {
      const raw = store.getItem(STORAGE_KEY);
      if (raw) {
        return JSON.parse(raw) as StoredSession;
      }
    } catch {
      // A blocked or corrupt store simply means "no session here".
    }
  }
  return null;
}

export function writeStoredSession(session: StoredSession, remember: boolean): void {
  clearStoredSession();
  try {
    const store = remember ? window.localStorage : window.sessionStorage;
    store.setItem(STORAGE_KEY, JSON.stringify(session));
  } catch {
    // Private browsing can refuse to store. The session still works for this
    // page load; it just will not be restored after a reload.
  }
}

export function clearStoredSession(): void {
  for (const store of [window.localStorage, window.sessionStorage]) {
    try {
      store.removeItem(STORAGE_KEY);
    } catch {
      // The session is dropped from memory regardless.
    }
  }
}

/** Where each role lands after logging in. */
export function homeRouteForRole(role: RoleName): string {
  switch (role) {
    case 'ADMIN':
      return '/admin';
    case 'SUPPORT_AGENT':
      return '/agent';
    case 'EMPLOYEE':
    default:
      return '/employee';
  }
}
