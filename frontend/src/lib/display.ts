import type { ToneName } from './tokens';
import type { IncidentStatus, Priority, SlaStatus } from './types';

/**
 * Vocabulary to tone, in one place. A status must not read green on one screen
 * and grey on another, so no component picks a colour of its own.
 */
export const STATUS_TONE: Record<IncidentStatus, ToneName> = {
  NEW: 'blue',
  ASSIGNED: 'purple',
  IN_PROGRESS: 'amber',
  RESOLVED: 'green',
  CLOSED: 'grey',
};

export const PRIORITY_TONE: Record<Priority, ToneName> = {
  LOW: 'grey',
  MEDIUM: 'blue',
  HIGH: 'amber',
  CRITICAL: 'red',
};

export const SLA_TONE: Record<SlaStatus, ToneName> = {
  ON_TRACK: 'green',
  AT_RISK: 'amber',
  BREACHED: 'red',
  COMPLETED: 'grey',
};

// ACTIVITY_LABEL used to live here: the client assembled each activity
// sentence from its parts. Descriptions are now generated server-side when the
// action happens, so the audit record and the screen cannot drift apart.

/** Enum names are shown to people, so underscores become spaces. */
export function humanise(value: string): string {
  return value.replace(/_/g, ' ');
}

export function formatDateTime(iso?: string): string {
  return iso ? new Date(iso).toLocaleString() : '--';
}

/**
 * The sentence shown beside an SLA stage.
 *
 * <p>The verdict itself comes from the server; this only turns a deadline and
 * that verdict into words. An overdue deadline never reads "0m remaining" - it
 * says how far past due it is.
 */
export function describeStage(stage: string, status?: SlaStatus, deadline?: string): string {
  if (!status || !deadline) {
    return `${stage}: no SLA`;
  }
  const diffMs = new Date(deadline).getTime() - Date.now();
  switch (status) {
    case 'BREACHED':
      return `${stage} SLA breached ${formatSpan(Math.abs(diffMs))} ago`;
    case 'AT_RISK':
      return `${stage} SLA due in ${formatSpan(Math.max(0, diffMs))}`;
    case 'COMPLETED':
      return `${stage} SLA met`;
    default:
      return `${stage} SLA due in ${formatSpan(Math.max(0, diffMs))}`;
  }
}

/** A duration in hours and minutes, never rounding a real gap down to zero. */
export function formatSpan(ms: number): string {
  const minutes = Math.max(1, Math.round(ms / 60000));
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours >= 24) {
    const days = Math.floor(hours / 24);
    return `${days}d ${hours % 24}h`;
  }
  return hours > 0 ? `${hours}h ${rest}m` : `${rest}m`;
}

/**
 * Time left against an SLA deadline, or how long it has been overdue.
 *
 * <p>Overdue is decided on the raw millisecond difference, not on the rounded
 * minutes: a deadline that passed 20 seconds ago rounds to zero minutes, and
 * testing the rounded value would report it as "0m left" when it is already
 * breached.
 */
export function formatDeadline(iso?: string): string {
  if (!iso) {
    return '--';
  }
  const diffMs = new Date(iso).getTime() - Date.now();
  const span = formatSpan(Math.abs(diffMs));
  return diffMs < 0 ? `Overdue by ${span}` : `${span} left`;
}
