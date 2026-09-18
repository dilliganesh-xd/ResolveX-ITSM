import axios, { type AxiosInstance } from 'axios';
import { clearStoredSession } from '@/auth/context';

/**
 * The one Axios instance for the app. Always relative to /api so the dev proxy
 * and the production reverse proxy behave identically.
 */
export const api: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
});

export function setAuthToken(token: string | null): void {
  if (token) {
    api.defaults.headers.common.Authorization = `Bearer ${token}`;
  } else {
    delete api.defaults.headers.common.Authorization;
  }
}

/**
 * An expired or revoked token must not leave the app showing a logged-in shell
 * that fails every request. On a 401 the session is dropped and the user is
 * returned to login - except on the login call itself, where a 401 just means
 * the password was wrong and the form should show the error.
 */
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error?.response?.status;
    const url: string = error?.config?.url ?? '';
    if (status === 401 && !url.includes('/auth/login')) {
      clearStoredSession();
      setAuthToken(null);
      if (window.location.pathname !== '/login') {
        window.location.assign('/login');
      }
    }
    return Promise.reject(error);
  },
);
