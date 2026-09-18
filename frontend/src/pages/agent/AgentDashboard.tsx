import { useCallback, useEffect, useState } from 'react';
import { listIncidents } from '@/api/incidents';
import { listTeams, type Team } from '@/api/admin';
import { Card } from '@/components/ui/Card';
import { Pagination } from '@/components/ui/Pagination';
import { EmptyState } from '@/components/ui/EmptyState';
import { IncidentFilterBar } from '@/components/ui/IncidentFilterBar';
import { IncidentTable } from '@/components/ui/IncidentTable';
import { PageHeader } from '@/components/ui/PageHeader';
import { useAuth } from '@/auth/useAuth';
import { color } from '@/lib/tokens';
import type { IncidentFilters, IncidentSummary } from '@/lib/types';

const PAGE_SIZE = 20;

/**
 * The agent queue. Filtering happens on the server, so the list stays correct
 * as it grows rather than being narrowed client-side after the fact.
 */
export function AgentDashboard() {
  const { user } = useAuth();
  const [incidents, setIncidents] = useState<IncidentSummary[] | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [teams, setTeams] = useState<Team[]>([]);
  const [filters, setFilters] = useState<IncidentFilters>({});
  const [error, setError] = useState<string | null>(null);

  const load = useCallback((applied: IncidentFilters, pageNumber: number) => {
    listIncidents(applied, pageNumber, PAGE_SIZE)
      .then((result) => {
        setIncidents(result.content);
        setPage(result.page);
        setTotalPages(result.totalPages);
        setTotalElements(result.totalElements);
        setError(null);
      })
      .catch(() => setError('Could not load the incident queue.'));
  }, []);

  // Search runs on a short debounce so typing does not fire a request per key.
  useEffect(() => {
    const timer = setTimeout(() => load(filters, 0), 250);
    return () => clearTimeout(timer);
  }, [filters, load]);

  useEffect(() => {
    listTeams().then(setTeams).catch(() => setTeams([]));
  }, []);

  const unassigned = incidents?.filter((i) => !i.assignedAgentName).length ?? 0;
  const atRisk = incidents?.filter((i) => i.slaStatus === 'AT_RISK').length ?? 0;
  const breached = incidents?.filter((i) => i.slaStatus === 'BREACHED').length ?? 0;
  const filtered = Boolean(filters.status || filters.priority || filters.teamId || filters.search);

  return (
    <>
      <PageHeader
        title="Incident Queue"
        subtitle={
          incidents
            ? `${totalElements} in your queue · ${unassigned} unassigned on this page · ${atRisk} at risk · ${breached} breached`
            : 'Loading the queue'
        }
      />

      <IncidentFilterBar filters={filters} teams={teams} onChange={setFilters} />

      <Card title="Queue" subtitle={user?.team ? `Team: ${user.team.name}` : 'No team assigned'}>
        {error && <div style={{ color: '#BE123C', fontWeight: 600, marginBottom: 10 }}>{error}</div>}

        {incidents === null && !error && <div style={{ color: color.muted, fontWeight: 600 }}>Loading...</div>}

        {incidents !== null && incidents.length === 0 && (
          <EmptyState
            title={filtered ? 'No incidents match' : 'Queue is clear'}
            description={
              filtered
                ? 'Try a different status, priority, team or search term.'
                : 'Nothing is routed to you or your team right now. New incidents appear here as they are reported.'
            }
          />
        )}

        {incidents !== null && incidents.length > 0 && (
          <>
            <IncidentTable incidents={incidents} showQueueColumns />
            <Pagination
              page={page}
              totalPages={totalPages}
              totalElements={totalElements}
              pageSize={PAGE_SIZE}
              shown={incidents.length}
              onChange={(next) => load(filters, next)}
            />
          </>
        )}
      </Card>
    </>
  );
}
