import { useCallback, useMemo, useRef, useState, type ReactNode } from 'react';
import { ToastContext, type Toast, type ToastContextValue, type ToastTone } from './toastContext';
import { color, font, radius, shadow, tone } from '@/lib/tokens';

const TONE_STYLE: Record<ToastTone, { bg: string; fg: string; line: string; glyph: string }> = {
  success: { bg: tone.green.bg, fg: tone.green.fg, line: tone.green.line, glyph: '✓' },
  error: { bg: tone.red.bg, fg: tone.red.fg, line: tone.red.line, glyph: '✕' },
  info: { bg: tone.blue.bg, fg: tone.blue.fg, line: tone.blue.line, glyph: 'i' },
};

const DISMISS_AFTER_MS = 4500;

/**
 * The one place the application says "that worked" or "that did not".
 *
 * <p>Before this there were per-page banners that each faded on their own timer;
 * one system means a confirmation looks the same wherever it comes from, and a
 * screen reader hears it once, from a live region that is always mounted.
 *
 * <p>Errors that a form needs to explain field by field still belong beside the
 * field. A toast is for the outcome of an action, not for validation.
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const timers = useRef<Map<string, number>>(new Map());

  const dismiss = useCallback((id: string) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
    const timer = timers.current.get(id);
    if (timer) {
      window.clearTimeout(timer);
      timers.current.delete(id);
    }
  }, []);

  const notify = useCallback(
    (message: string, toastTone: ToastTone = 'success') => {
      const id = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
      setToasts((current) => [...current, { id, tone: toastTone, message }]);
      timers.current.set(
        id,
        window.setTimeout(() => dismiss(id), DISMISS_AFTER_MS),
      );
    },
    [dismiss],
  );

  const value = useMemo<ToastContextValue>(() => ({ notify, dismiss }), [notify, dismiss]);

  return (
    <ToastContext.Provider value={value}>
      {children}

      {/* Always mounted, so an assistive technology announces each message as it
          arrives rather than only noticing the region appear. */}
      <div
        role="status"
        aria-live="polite"
        style={{
          position: 'fixed',
          right: 16,
          bottom: 16,
          display: 'grid',
          gap: 8,
          zIndex: 300,
          maxWidth: 'min(360px, calc(100vw - 32px))',
        }}
      >
        {toasts.map((toast) => {
          const style = TONE_STYLE[toast.tone];
          return (
            <div
              key={toast.id}
              style={{
                display: 'flex',
                alignItems: 'flex-start',
                gap: 9,
                background: style.bg,
                border: `1px solid ${style.line}`,
                borderRadius: radius.control,
                boxShadow: shadow.card,
                padding: '10px 12px',
              }}
            >
              <span
                aria-hidden
                style={{
                  width: 18,
                  height: 18,
                  flexShrink: 0,
                  borderRadius: 5,
                  background: style.fg,
                  color: style.bg,
                  fontSize: 11,
                  fontWeight: 800,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  marginTop: 1,
                }}
              >
                {style.glyph}
              </span>
              <span style={{ fontSize: 12.5, fontWeight: 600, color: style.fg, minWidth: 0 }}>
                {toast.message}
              </span>
              <button
                type="button"
                onClick={() => dismiss(toast.id)}
                aria-label="Dismiss notification"
                style={{
                  marginLeft: 'auto',
                  border: 'none',
                  background: 'transparent',
                  color: style.fg,
                  cursor: 'pointer',
                  fontFamily: font.sans,
                  fontSize: 14,
                  lineHeight: 1,
                  padding: 2,
                }}
              >
                ×
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

/** Used by the shell so the toast layer never covers the page background. */
export const toastSafeArea = { paddingBottom: 8, background: color.pageBg } as const;
