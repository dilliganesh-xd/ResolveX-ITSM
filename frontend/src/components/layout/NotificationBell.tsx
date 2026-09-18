import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { listNotifications, markAllRead, markRead, unreadCount } from '@/api/notifications';
import { Button } from '@/components/ui/Button';
import { useToast } from '@/feedback/useToast';
import { formatDateTime } from '@/lib/display';
import { color, font, radius, tone } from '@/lib/tokens';
import type { AppNotification, NotificationType } from '@/lib/types';

/** Each type carries a glyph as well as a tone, so meaning never rests on colour. */
const TYPE_MARK: Record<NotificationType, { glyph: string; tint: string }> = {
  INCIDENT_CREATED: { glyph: '+', tint: tone.blue.dot },
  INCIDENT_ASSIGNED: { glyph: '→', tint: tone.purple.dot },
  INCIDENT_UPDATED: { glyph: '±', tint: tone.blue.dot },
  COMMENT_ADDED: { glyph: '”', tint: tone.blue.dot },
  SLA_AT_RISK: { glyph: '!', tint: tone.amber.dot },
  SLA_BREACHED: { glyph: '✕', tint: tone.red.dot },
  INCIDENT_RESOLVED: { glyph: '✓', tint: tone.green.dot },
  INCIDENT_CLOSED: { glyph: '✓', tint: tone.grey.dot },
};

/**
 * The notification bell.
 *
 * <p>The badge count is polled on a slow interval - a minute - because this is
 * an internal tool and a websocket for a number that changes a few times a day
 * would be more machinery than the problem deserves. Opening the panel fetches
 * the list fresh, so what you read is never the stale copy behind the badge.
 */
export function NotificationBell() {
  const navigate = useNavigate();
  const { notify: toast } = useToast();
  const [open, setOpen] = useState(false);
  const [unread, setUnread] = useState(0);
  const [items, setItems] = useState<AppNotification[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const panelRef = useRef<HTMLDivElement | null>(null);

  const refreshCount = useCallback(() => {
    unreadCount()
      .then(setUnread)
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    refreshCount();
    const timer = window.setInterval(refreshCount, 60000);
    return () => window.clearInterval(timer);
  }, [refreshCount]);

  // Escape closes the panel, and clicking outside it does too.
  useEffect(() => {
    if (!open) {
      return;
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpen(false);
      }
    }
    function onClick(event: MouseEvent) {
      if (panelRef.current && !panelRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    window.addEventListener('keydown', onKey);
    window.addEventListener('mousedown', onClick);
    return () => {
      window.removeEventListener('keydown', onKey);
      window.removeEventListener('mousedown', onClick);
    };
  }, [open]);

  function toggle() {
    const next = !open;
    setOpen(next);
    if (next) {
      setItems(null);
      setError(null);
      listNotifications()
        .then(setItems)
        .catch(() => setError('Could not load notifications.'));
    }
  }

  async function openIncident(notification: AppNotification) {
    if (!notification.read) {
      await markRead(notification.id).catch(() => undefined);
      refreshCount();
    }
    setOpen(false);
    if (notification.relatedIncidentId) {
      navigate(`/incidents/${notification.relatedIncidentId}`);
    }
  }

  return (
    <div ref={panelRef} style={{ position: 'relative' }}>
      <button
        type="button"
        onClick={toggle}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-label={unread > 0 ? `Notifications, ${unread} unread` : 'Notifications, none unread'}
        style={{
          position: 'relative',
          height: 34,
          padding: '0 10px',
          borderRadius: radius.control,
          border: `1px solid ${color.borderInput}`,
          background: color.surface,
          color: color.subtle,
          fontFamily: font.sans,
          fontSize: 12.5,
          fontWeight: 700,
          cursor: 'pointer',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 7,
        }}
      >
        <span aria-hidden style={{ fontSize: 14 }}>🔔</span>
        {unread > 0 && (
          <span
            style={{
              minWidth: 18,
              height: 18,
              padding: '0 5px',
              borderRadius: 9,
              background: tone.red.dot,
              color: '#FFFFFF',
              fontFamily: font.mono,
              fontSize: 10.5,
              fontWeight: 700,
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>

      {open && (
        <div
          role="dialog"
          aria-label="Notifications"
          className="notification-panel"
          style={{
            position: 'absolute',
            top: 'calc(100% + 6px)',
            right: 0,
            width: 340,
            maxHeight: 420,
            overflowY: 'auto',
            background: color.surface,
            border: `1px solid ${color.border}`,
            borderRadius: radius.card,
            boxShadow: '0 4px 14px rgba(16,24,40,.10)',
            zIndex: 150,
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 8,
              padding: '12px 14px',
              borderBottom: `1px solid ${color.hairline}`,
              position: 'sticky',
              top: 0,
              background: color.surface,
            }}
          >
            <strong style={{ fontSize: 13, color: color.heading }}>Notifications</strong>
            {unread > 0 && (
              <Button
                variant="secondary"
                style={{ height: 28, fontSize: 11.5 }}
                onClick={() => {
                  markAllRead()
                    .then(() => {
                      setItems((current) =>
                        current ? current.map((item) => ({ ...item, read: true })) : current,
                      );
                      refreshCount();
                      toast('All notifications marked as read.');
                    })
                    .catch(() => setError('Could not update notifications.'));
                }}
              >
                Mark all as read
              </Button>
            )}
          </div>

          <div style={{ padding: 8 }}>
            {error && (
              <div style={{ padding: 10, fontSize: 12.5, fontWeight: 600, color: '#BE123C' }}>{error}</div>
            )}

            {!error && items === null && (
              <div style={{ padding: 10, fontSize: 12.5, fontWeight: 600, color: color.muted }}>
                Loading...
              </div>
            )}

            {!error && items !== null && items.length === 0 && (
              <div style={{ padding: '18px 10px', textAlign: 'center' }}>
                <div style={{ fontSize: 13, fontWeight: 700, color: color.body }}>
                  You&rsquo;re all caught up.
                </div>
                <div style={{ fontSize: 12, color: color.faint, fontWeight: 500, marginTop: 4 }}>
                  You will be told here when an incident needs you.
                </div>
              </div>
            )}

            {!error &&
              items?.map((item) => {
                const mark = TYPE_MARK[item.type];
                return (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => openIncident(item)}
                    style={{
                      display: 'flex',
                      gap: 9,
                      width: '100%',
                      textAlign: 'left',
                      padding: '9px 10px',
                      border: 'none',
                      borderRadius: radius.control,
                      background: item.read ? 'transparent' : color.brandSofter,
                      cursor: 'pointer',
                      fontFamily: font.sans,
                    }}
                  >
                    <span
                      aria-hidden
                      style={{
                        width: 18,
                        height: 18,
                        flexShrink: 0,
                        borderRadius: 5,
                        background: mark.tint,
                        color: '#FFFFFF',
                        fontSize: 11,
                        fontWeight: 700,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        marginTop: 1,
                      }}
                    >
                      {mark.glyph}
                    </span>
                    <span style={{ minWidth: 0 }}>
                      <span
                        style={{
                          display: 'block',
                          fontSize: 12.5,
                          fontWeight: item.read ? 600 : 800,
                          color: color.heading,
                        }}
                      >
                        {item.title}
                        {!item.read && <span style={{ color: tone.red.fg }}> ·</span>}
                      </span>
                      <span style={{ display: 'block', fontSize: 12, color: color.muted, marginTop: 2 }}>
                        {item.message}
                      </span>
                      <span
                        style={{
                          display: 'block',
                          fontFamily: font.mono,
                          fontSize: 10.5,
                          color: color.faint,
                          marginTop: 3,
                        }}
                      >
                        {formatDateTime(item.createdAt)}
                        {item.relatedIncidentReference ? ` · ${item.relatedIncidentReference}` : ''}
                      </span>
                    </span>
                  </button>
                );
              })}
          </div>
        </div>
      )}
    </div>
  );
}
