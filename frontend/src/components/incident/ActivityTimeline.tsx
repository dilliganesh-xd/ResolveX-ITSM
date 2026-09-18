import { Card } from '@/components/ui/Card';
import { color, font, tone } from '@/lib/tokens';
import type { Activity, ActivityType } from '@/lib/types';

/**
 * The audit trail, oldest first so it reads as a story.
 *
 * <p>Descriptions are rendered exactly as the server wrote them. Composing the
 * sentence here would mean the record and the screen could disagree, and the
 * record is the one that matters.
 */
export function ActivityTimeline({ activity }: { activity: Activity[] }) {
  return (
    <Card title="Activity" subtitle={`${activity.length} recorded`}>
      {activity.length === 0 ? (
        <div style={{ color: color.faint, fontWeight: 600 }}>Nothing recorded yet.</div>
      ) : (
        <ol
          style={{
            margin: 0,
            padding: '0 0 0 4px',
            listStyle: 'none',
            display: 'grid',
            gap: 14,
            position: 'relative',
          }}
        >
          {activity.map((entry, index) => (
            <li key={entry.id} style={{ display: 'flex', gap: 10, position: 'relative' }}>
              {/* The rail joins consecutive entries, but must not dangle past the last one. */}
              {index < activity.length - 1 && (
                <span
                  aria-hidden
                  style={{
                    position: 'absolute',
                    left: 3,
                    top: 12,
                    bottom: -14,
                    width: 1,
                    background: color.border,
                  }}
                />
              )}
              <span
                aria-hidden
                style={{
                  width: 7,
                  height: 7,
                  borderRadius: '50%',
                  background: dotFor(entry.action),
                  marginTop: 5,
                  flexShrink: 0,
                  position: 'relative',
                }}
              />
              <div style={{ minWidth: 0 }}>
                <div style={{ fontFamily: font.mono, fontSize: 10.5, color: color.faint }}>
                  {formatTime(entry.createdAt)}
                </div>
                <div style={{ fontSize: 12.5, color: color.body, fontWeight: 600, marginTop: 2 }}>
                  {entry.description}
                </div>
              </div>
            </li>
          ))}
        </ol>
      )}
    </Card>
  );
}

/** The marker takes the tone of what happened, from the shared palette. */
function dotFor(action: ActivityType): string {
  switch (action) {
    case 'INCIDENT_RESOLVED':
    case 'SLA_COMPLETED':
      return tone.green.dot;
    case 'SLA_BREACHED':
      return tone.red.dot;
    case 'INCIDENT_REOPENED':
    case 'PRIORITY_CHANGED':
      return tone.amber.dot;
    case 'INCIDENT_CLOSED':
      return tone.grey.dot;
    default:
      return color.brand;
  }
}

/** Date and time, since a trail can span days. */
function formatTime(iso: string): string {
  const at = new Date(iso);
  const today = new Date().toDateString() === at.toDateString();
  return today
    ? at.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
    : at.toLocaleString([], {
        month: 'short',
        day: 'numeric',
        hour: 'numeric',
        minute: '2-digit',
      });
}
