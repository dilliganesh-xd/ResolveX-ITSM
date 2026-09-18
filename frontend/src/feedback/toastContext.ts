import { createContext } from 'react';

export type ToastTone = 'success' | 'error' | 'info';

export interface Toast {
  id: string;
  tone: ToastTone;
  message: string;
}

export interface ToastContextValue {
  /** Shows a message. Success is the default because most of them are. */
  notify: (message: string, tone?: ToastTone) => void;
  dismiss: (id: string) => void;
}

export const ToastContext = createContext<ToastContextValue | null>(null);
