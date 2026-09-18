import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getIncidentStats, listIncidents, type IncidentStats } from '@/api/incidents';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorBanner } from '@/components/ui/Feedback';
import { IncidentTable } from '@/components/ui/IncidentTable';
import { PageHeader } from '@/components/ui/PageHeader';
import { StatCard } from '@/components/ui/StatCard';
import { StatsSkeleton, TableSkeleton } from '@/components/ui/Skeleton';
import { useAuth } from '@/auth/useAuth';
import type { IncidentSummary } from '@/lib/types';
import { IconAlert, IconCheck, IconClock, IconInbox, IconQueue, IconTicket } from '@/components/ui/icons';

/**
 * The agent's operational view: what is on fire, what nobody has picked up, and
 * what they have already finished today.
 *
 * <p>Separate from the queue on purpose. This screen answers "where should I
 * look first"; the queue is for working through the list.
 */
export function AgentOverview() {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [stats, setStats] = useState<IncidentStats | null>(null);
  const [needsAttention, setNeedsAttention] = useState<IncidentSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getIncidentStats()
      .then(setStats)
      .catch(() => setError('Could not load your dashboard.'));

    // Breached first: if anything has already missed its SLA, that is what the
    // agent should be looking at before anything else.
    listIncidents({ slaStatus: 'BREACHED' }, 0, 5)
      .then((breached) => {
        if (breached.content.length > 0) {
          setNeedsAttention(breached.content);
          return;
        }
        listIncidents({ slaStatus: 'AT_RISK' }, 0, 5)
          .then((atRisk) => setNeedsAttention(atRisk.content))
          .catch(() => setNeedsAttention([]));
      })
      .catch(() => setNeedsAttention([]));
  }, []);

  return (
    <>
      <PageHeader
        title={`Good to see you, ${user?.name ?? ''}`}
        subtitle={user?.team ? `${user.team.name} team` : 'No team assigned'}
        action={<Button onClick={() => navigate('/agent/queue')}>Open incident queue</Button>}
      />

      {error && <ErrorBanner message={error} />}

      {stats === null ? (
        <StatsSkeleton count={6} />
      ) : (
        <>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(148px, 1fr))',
              gap: 12,
            }}
          >
            <StatCard label="Assigned to me" value={stats.assignedToMe} icon={IconInbox} />
            <StatCard label="Unassigned" value={stats.unassigned} hint="Waiting for triage" icon={IconQueue} />
            <StatCard label="Open" value={stats.open} icon={IconTicket} />
            <StatCard label="In progress" value={stats.inProgress} icon={IconClock} />
          </div>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(148px, 1fr))',
              gap: 12,
              marginTop: 12,
            }}
          >
            <StatCard label="SLA at risk" value={stats.atRisk} toneName="amber" icon={IconClock} />
            <StatCard label="SLA breached" value={stats.breached} toneName="red" icon={IconAlert} />
            <StatCard label="Resolved today" value={stats.resolvedToday} toneName="green" icon={IconCheck} />
          </div>
        </>
      )}

      <div style={{ marginTop: 14 }}>
        <Card
          title="Needs attention"
          subtitle="Breached first, then at risk"
          action={
            <Button variant="secondary" style={{ height: 30, fontSize: 12 }} onClick={() => navigate('/agent/queue')}>
              View all
            </Button>
          }
        >
          {needsAttention === null && !error && <TableSkeleton rows={3} columns={6} />}

          {needsAttention !== null && needsAttention.length === 0 && (
            <EmptyState
              title="Nothing is behind"
              description="No incident in your queue is at risk or breached. The queue has the rest of your work."
              action={<Button variant="secondary" onClick={() => navigate('/agent/queue')}>Open queue</Button>}
            />
          )}

          {needsAttention !== null && needsAttention.length > 0 && (
            <IncidentTable incidents={needsAttention} showQueueColumns />
          )}
        </Card>
      </div>
    </>
  );
}
