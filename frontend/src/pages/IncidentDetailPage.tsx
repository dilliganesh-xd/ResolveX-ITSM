import { useCallback, useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import axios from 'axios';
import { getIncident, listActivities, listComments } from '@/api/incidents';
import { listTeams, listAgents, type AdminUser, type Team } from '@/api/admin';
import { Card } from '@/components/ui/Card';
import { PageHeader } from '@/components/ui/PageHeader';
import { StatusPill } from '@/components/ui/StatusPill';
import { CommentThread } from '@/components/incident/CommentThread';
import { ActivityTimeline } from '@/components/incident/ActivityTimeline';
import { AgentControls } from '@/components/incident/AgentControls';
import { SlaPanel } from '@/components/incident/SlaPanel';
import { useAuth } from '@/auth/useAuth';
import { PRIORITY_TONE, STATUS_TONE, formatDateTime, humanise } from '@/lib/display';
import { color, font, radius } from '@/lib/tokens';
import type { Activity, ApiError, Comment, IncidentDetail } from '@/lib/types';

/**
 * One detail page for all three roles. The panels a role may not use are simply
 * not rendered; the API rejects the same calls independently, so hiding a
 * control is a convenience, never the protection.
 */
export function IncidentDetailPage() {
  const { id = '' } = useParams();
  const { user } = useAuth();
  const [incident, setIncident] = useState<IncidentDetail | null>(null);
  const [comments, setComments] = useState<Comment[]>([]);
  const [activity, setActivity] = useState<Activity[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [teams, setTeams] = useState<Team[]>([]);
  const [agents, setAgents] = useState<AdminUser[]>([]);

  const canAct = user?.role === 'SUPPORT_AGENT' || user?.role === 'ADMIN';

  /**
   * The thread and the trail come from their own endpoints rather than only
   * from the incident payload, so posting a comment can refresh those two
   * without refetching everything.
   */
  const loadThread = useCallback(() => {
    listComments(id).then(setComments).catch(() => undefined);
    listActivities(id).then(setActivity).catch(() => undefined);
  }, [id]);

  const load = useCallback(() => {
    getIncident(id)
      .then((data) => {
        setIncident(data);
        setError(null);
        loadThread();
      })
      .catch((caught) => {
        if (axios.isAxiosError<ApiError>(caught) && caught.response?.status === 403) {
          setError('You do not have access to this incident.');
        } else if (axios.isAxiosError<ApiError>(caught) && caught.response?.status === 404) {
          setError('That incident does not exist.');
        } else {
          setError('Could not load the incident.');
        }
      });
  }, [id, loadThread]);

  useEffect(load, [load]);

  // Reference data for the assignment controls, fetched only for roles that
  // have those controls at all.
  useEffect(() => {
    if (!canAct) {
      return;
    }
    listTeams().then(setTeams).catch(() => setTeams([]));
    listAgents().then(setAgents).catch(() => setAgents([]));
  }, [canAct]);

  if (error) {
    return (
      <>
        <PageHeader title="Incident" />
        <Card>
          <div style={{ fontWeight: 700, color: color.heading, fontSize: 13 }}>{error}</div>
          <p style={{ margin: '6px 0 0', fontSize: 12.5, color: color.muted }}>
            <Link to="/" style={{ color: color.brand }}>
              Back to your dashboard
            </Link>
          </p>
        </Card>
      </>
    );
  }

  if (!incident) {
    return (
      <>
        <PageHeader title="Incident" subtitle="Loading" />
        <Card>
          <div style={{ display: 'grid', gap: 11 }}>
            {['70%', '85%', '45%'].map((width) => (
              <span key={width} style={{ display: 'block', width, height: 11, borderRadius: 6, background: '#EEF1F5' }} />
            ))}
          </div>
        </Card>
      </>
    );
  }

  return (
    <>
      <PageHeader
        title={incident.title}
        subtitle={`${incident.reference} · reported by ${incident.reporterName} · ${formatDateTime(incident.createdAt)}`}
        action={
          <div style={{ display: 'flex', gap: 6 }}>
            <StatusPill label={humanise(incident.status)} toneName={STATUS_TONE[incident.status]} />
            <StatusPill label={incident.priority} toneName={PRIORITY_TONE[incident.priority]} />
          </div>
        }
      />

      <div
        className="detail-grid"
        style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 2fr) minmax(260px, 1fr)', gap: 14 }}
      >
        <div style={{ display: 'grid', gap: 14, minWidth: 0 }}>
          <Card title="Description" subtitle={`${incident.category}${incident.subcategory ? ` · ${incident.subcategory}` : ''}`}>
            <p style={{ margin: 0, whiteSpace: 'pre-wrap', lineHeight: 1.7 }}>{incident.description}</p>
          </Card>

          {incident.resolutionSummary && (
            <Card title="Resolution" subtitle={`Resolved ${formatDateTime(incident.resolvedAt)}`}>
              <p style={{ margin: 0, whiteSpace: 'pre-wrap', lineHeight: 1.7 }}>{incident.resolutionSummary}</p>
            </Card>
          )}

          {canAct && <AgentControls incident={incident} teams={teams} agents={agents} onChanged={load} />}

          <CommentThread
            incidentId={incident.id}
            status={incident.status}
            comments={comments}
            canPostInternal={canAct}
            onPosted={loadThread}
          />
        </div>

        <div style={{ display: 'grid', gap: 14, minWidth: 0 }}>
          <Card title="Incident information">
            <dl style={{ margin: 0, display: 'grid', gap: 10 }}>
              <Row label="Incident ID" value={incident.reference} mono />
              <Row label="Status" value={humanise(incident.status)} />
              <Row label="Priority" value={incident.priority} />
              <Row label="Category" value={incident.category} />
              <Row label="Subcategory" value={incident.subcategory} />
              <Row label="Reporter" value={incident.reporterName} />
              <Row label="Assigned team" value={incident.assignedTeam?.name} fallback="Unrouted" />
              <Row label="Assigned agent" value={incident.assignedAgentName} fallback="Unassigned" />
              <Row label="Created" value={formatDateTime(incident.createdAt)} mono />
              <Row label="Updated" value={formatDateTime(incident.updatedAt)} mono />
              {incident.resolvedAt && <Row label="Resolved" value={formatDateTime(incident.resolvedAt)} mono />}
              {incident.closedAt && <Row label="Closed" value={formatDateTime(incident.closedAt)} mono />}
            </dl>
          </Card>

          <SlaPanel incident={incident} showResponseStage={canAct} />

          <ActivityTimeline activity={activity} />
        </div>
      </div>
    </>
  );
}

function Row({
  label,
  value,
  fallback = '--',
  mono,
}: {
  label: string;
  value?: string;
  fallback?: string;
  mono?: boolean;
}) {
  return (
    <div style={{ display: 'flex', gap: 10, alignItems: 'baseline' }}>
      <dt style={{ minWidth: 104, flexShrink: 0, fontSize: 11.5, fontWeight: 700, color: color.subtle }}>
        {label}
      </dt>
      <dd
        style={{
          margin: 0,
          fontSize: mono ? 11.5 : 12.5,
          fontFamily: mono ? font.mono : font.sans,
          fontWeight: 600,
          color: value ? color.heading : color.faint,
          borderRadius: radius.small,
          wordBreak: 'break-word',
        }}
      >
        {value ?? fallback}
      </dd>
    </div>
  );
}
