import { useState, type FormEvent } from 'react';
import axios from 'axios';
import {
  assignIncident,
  changeStatus,
  closeIncident,
  resolveIncident,
  updateIncident,
} from '@/api/incidents';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { useAuth } from '@/auth/useAuth';
import { useToast } from '@/feedback/useToast';
import { humanise } from '@/lib/display';
import { color, font, radius, size } from '@/lib/tokens';
import type { AdminUser, Team } from '@/api/admin';
import type { ApiError, IncidentDetail, IncidentStatus, Priority } from '@/lib/types';

const PRIORITIES: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const CATEGORIES = ['Network', 'Hardware', 'Software', 'Access', 'Email', 'Database', 'Other'];

interface AgentControlsProps {
  incident: IncidentDetail;
  teams: Team[];
  agents: AdminUser[];
  onChanged: () => void;
}

/**
 * Everything an agent or admin can do to an incident.
 *
 * <p>The status control only offers the transitions the API will accept, so the
 * screen cannot suggest a move that is about to be rejected. Resolve and close
 * are separate actions because each has its own requirement.
 */
export function AgentControls({ incident, teams, agents, onChanged }: AgentControlsProps) {
  const { user } = useAuth();
  const { notify } = useToast();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [agentId, setAgentId] = useState(incident.assignedAgentId ?? '');
  const [teamId, setTeamId] = useState(incident.assignedTeam?.id ?? '');
  const [priority, setPriority] = useState<Priority>(incident.priority);
  const [category, setCategory] = useState(incident.category);
  const [resolution, setResolution] = useState('');
  const [closeNote, setCloseNote] = useState('');

  const closed = incident.status === 'CLOSED';
  const resolved = incident.status === 'RESOLVED';
  const isAdmin = user?.role === 'ADMIN';
  const mine = incident.assignedAgentId === user?.userId;

  /** Mirrors the transitions the service allows for the status endpoint. */
  const nextStatuses: IncidentStatus[] = (() => {
    switch (incident.status) {
      case 'NEW':
        return ['ASSIGNED', 'IN_PROGRESS'];
      case 'ASSIGNED':
        return ['IN_PROGRESS'];
      case 'IN_PROGRESS':
        return ['ASSIGNED'];
      case 'RESOLVED':
        return ['IN_PROGRESS'];
      default:
        return [];
    }
  })();

  async function run(action: () => Promise<unknown>, done?: string) {
    setBusy(true);
    setError(null);
    try {
      await action();
      if (done) {
        notify(done);
      }
      onChanged();
    } catch (caught) {
      const message = axios.isAxiosError<ApiError>(caught)
        ? caught.response?.data?.message ?? 'That action was rejected.'
        : 'That action was rejected.';
      setError(message);
      notify(message, 'error');
    } finally {
      setBusy(false);
    }
  }

  if (closed) {
    return (
      <Card title="Actions" subtitle="This incident is closed">
        <div style={{ fontSize: 12.5, color: color.muted, fontWeight: 600 }}>
          A closed incident is final in V1. Create a new incident if the problem returns.
        </div>
      </Card>
    );
  }

  return (
    <Card title="Actions" subtitle="Assign, update, resolve or close">
      {error && (
        <div
          role="alert"
          style={{
            marginBottom: 12,
            padding: '9px 11px',
            borderRadius: radius.control,
            background: '#FEF2F2',
            color: '#BE123C',
            fontSize: 12.5,
            fontWeight: 600,
          }}
        >
          {error}
        </div>
      )}

      {/* Assignment.

          An agent takes the incident or leaves it in the pool; only an admin
          points work at someone else or moves it between teams. The API
          enforces the same rule, so this is a convenience, not the control. */}
      {isAdmin ? (
        <Section label="Assignment">
          <select value={teamId} onChange={(e) => setTeamId(e.target.value)} style={control} aria-label="Team">
            <option value="">Select team</option>
            {teams.map((team) => (
              <option key={team.id} value={team.id}>
                {team.name}
              </option>
            ))}
          </select>

          <select value={agentId} onChange={(e) => setAgentId(e.target.value)} style={control} aria-label="Agent">
            <option value="">Select agent</option>
            {agents.map((agent) => (
              <option key={agent.id} value={agent.id}>
                {agent.name}
              </option>
            ))}
          </select>

          <Button
            type="button"
            disabled={busy || (!agentId && !teamId)}
            onClick={() =>
              run(() =>
                assignIncident(incident.id, {
                  agentId: agentId || undefined,
                  teamId: teamId || undefined,
                }),
                'Incident assigned.',
              )
            }
          >
            Assign
          </Button>
        </Section>
      ) : (
        <Section label="Assignment">
          {mine ? (
            <span style={{ fontSize: 12.5, fontWeight: 600, color: color.muted }}>
              You are working this incident.
            </span>
          ) : incident.assignedAgentId ? (
            <span style={{ fontSize: 12.5, fontWeight: 600, color: color.muted }}>
              {incident.assignedAgentName ?? 'Another agent'} is working this. An administrator can
              reassign it.
            </span>
          ) : (
            <>
              <Button
                type="button"
                disabled={busy}
                onClick={() =>
                  run(() => assignIncident(incident.id, { agentId: user?.userId }), 'You have taken this incident.')
                }
              >
                Accept incident
              </Button>
              <span style={{ fontSize: 12, fontWeight: 500, color: color.faint }}>
                Taking it makes you the owner and starts your clock.
              </span>
            </>
          )}
        </Section>
      )}

      {/* Priority and category */}
      <Section label="Update">
        <select
          value={priority}
          onChange={(e) => setPriority(e.target.value as Priority)}
          style={control}
          aria-label="Priority"
        >
          {PRIORITIES.map((value) => (
            <option key={value} value={value}>
              {value}
            </option>
          ))}
        </select>

        <select value={category} onChange={(e) => setCategory(e.target.value)} style={control} aria-label="Category">
          {[...new Set([incident.category, ...CATEGORIES])].map((value) => (
            <option key={value} value={value}>
              {value}
            </option>
          ))}
        </select>

        <Button
          type="button"
          variant="secondary"
          disabled={busy || (priority === incident.priority && category === incident.category)}
          onClick={() => run(() => updateIncident(incident.id, { priority, category }), 'Incident updated.')}
        >
          Save changes
        </Button>
      </Section>

      {/* Status */}
      {nextStatuses.length > 0 && (
        <Section label="Status">
          {nextStatuses.map((status) => (
            <Button
              key={status}
              type="button"
              variant="secondary"
              disabled={busy}
              onClick={() => run(() => changeStatus(incident.id, status), 'Status updated.')}
            >
              {incident.status === 'RESOLVED' ? 'Reopen' : `Mark ${humanise(status).toLowerCase()}`}
            </Button>
          ))}
        </Section>
      )}

      {/* Resolve */}
      {!resolved && (
        <form
          onSubmit={(event: FormEvent) => {
            event.preventDefault();
            run(() => resolveIncident(incident.id, resolution.trim()), 'Incident resolved.');
          }}
          style={{ marginTop: 14 }}
        >
          <SectionLabel>Resolve</SectionLabel>
          <textarea
            rows={2}
            value={resolution}
            onChange={(e) => setResolution(e.target.value)}
            placeholder="Resolution summary (required)"
            aria-label="Resolution summary"
            style={{ ...control, width: '100%', height: 'auto', padding: '9px 11px', resize: 'vertical' }}
          />
          <div style={{ marginTop: 8 }}>
            <Button type="submit" disabled={busy || !resolution.trim()}>
              Resolve incident
            </Button>
          </div>
        </form>
      )}

      {/* Close */}
      {resolved && (
        <form
          onSubmit={(event: FormEvent) => {
            event.preventDefault();
            run(() => closeIncident(incident.id, closeNote.trim() || undefined), 'Incident closed.');
          }}
          style={{ marginTop: 14 }}
        >
          <SectionLabel>Close</SectionLabel>
          <input
            value={closeNote}
            onChange={(e) => setCloseNote(e.target.value)}
            placeholder="Closing note (optional)"
            aria-label="Closing note"
            style={{ ...control, width: '100%' }}
          />
          <div style={{ marginTop: 8 }}>
            <Button type="submit" disabled={busy}>
              Close incident
            </Button>
          </div>
        </form>
      )}
    </Card>
  );
}

function Section({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 14 }}>
      <SectionLabel>{label}</SectionLabel>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>{children}</div>
    </div>
  );
}

function SectionLabel({ children }: { children: React.ReactNode }) {
  return (
    <div
      style={{
        fontFamily: font.mono,
        fontSize: 10.5,
        fontWeight: 600,
        letterSpacing: '0.08em',
        textTransform: 'uppercase',
        color: color.faint,
        marginBottom: 8,
      }}
    >
      {children}
    </div>
  );
}

const control = {
  height: size.control,
  padding: '0 11px',
  border: `1px solid ${color.borderInput}`,
  borderRadius: radius.control,
  fontSize: 13,
  fontFamily: font.sans,
  background: color.surface,
  color: color.heading,
} as const;
