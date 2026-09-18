import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getIncidentStats, listIncidents, type IncidentStats } from '@/api/incidents';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorBanner } from '@/components/ui/Feedback';
import { IncidentTable } from '@/components/ui/IncidentTable';
import { PageHeader } from '@/components/ui/PageHeader';
import { Pagination } from '@/components/ui/Pagination';
import { StatCard } from '@/components/ui/StatCard';
import { StatsSkeleton, TableSkeleton } from '@/components/ui/Skeleton';
import { useAuth } from '@/auth/useAuth';
import { IconAlert, IconCheck, IconClock, IconQueue, IconTicket } from '@/components/ui/icons';
import { color, font, radius, size } from '@/lib/tokens';
import type {
  IncidentFilters,
  IncidentStatus,
  IncidentSummary,
  Priority,
  SlaStatus,
} from '@/lib/types';

const PAGE_SIZE = 10;
const STATUSES: IncidentStatus[] = ['NEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];
const PRIORITIES: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const SLA_STATUSES: SlaStatus[] = ['ON_TRACK', 'AT_RISK', 'BREACHED', 'COMPLETED'];

/**
 * What a reporter sees on arrival: how their incidents stand, whether any are in
 * SLA trouble, and the list itself.
 *
 * <p>The counts come from the server over the same role-scoped set as the table,
 * so a card and the rows beneath it cannot disagree.
 */
export function EmployeeDashboard() {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [stats, setStats] = useState<IncidentStats | null>(null);
  const [incidents, setIncidents] = useState<IncidentSummary[] | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
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
      .catch(() => setError('Could not load your incidents.'));
  }, []);

  // A filter change returns to the first page: page 3 of the previous result
  // set means nothing once the set has changed.
  useEffect(() => {
    const timer = setTimeout(() => load(filters, 0), 250);
    return () => clearTimeout(timer);
  }, [filters, load]);

  useEffect(() => {
    getIncidentStats()
      .then(setStats)
      .catch(() => undefined);
  }, []);

  const filtering = Boolean(filters.search || filters.status || filters.priority || filters.slaStatus);

  return (
    <>
      <PageHeader
        title={`Welcome, ${user?.name ?? ''}`}
        subtitle="Your reported incidents and how they are tracking"
        action={<Button onClick={() => navigate('/employee/new')}>Report incident</Button>}
      />

      {error && <ErrorBanner message={error} />}

      {stats === null ? (
        <StatsSkeleton count={5} />
      ) : (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(148px, 1fr))',
            gap: 12,
          }}
        >
          <StatCard label="Total reported" value={stats.total} icon={IconTicket} />
          <StatCard label="Open" value={stats.open} icon={IconQueue} />
          <StatCard label="In progress" value={stats.inProgress} icon={IconClock} />
          <StatCard label="Resolved" value={stats.resolved} icon={IconCheck} />
          <StatCard label="Closed" value={stats.closed} icon={IconCheck} />
        </div>
      )}

      {stats !== null && (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(148px, 1fr))',
            gap: 12,
            marginTop: 12,
          }}
        >
          <StatCard label="SLA on track" value={stats.onTrack} toneName="green" icon={IconCheck} />
          <StatCard label="SLA at risk" value={stats.atRisk} toneName="amber" icon={IconClock} />
          <StatCard label="SLA breached" value={stats.breached} toneName="red" icon={IconAlert} />
        </div>
      )}

      <form
        onSubmit={(event) => event.preventDefault()}
        style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center', margin: '18px 0 14px' }}
      >
        <input
          type="search"
          placeholder="Search your incidents"
          value={filters.search ?? ''}
          onChange={(e) => setFilters({ ...filters, search: e.target.value })}
          aria-label="Search your incidents"
          style={{ ...control, flex: '0 1 240px' }}
        />
        <select
          value={filters.status ?? ''}
          onChange={(e) => setFilters({ ...filters, status: e.target.value as IncidentStatus | '' })}
          aria-label="Filter by status"
          style={control}
        >
          <option value="">All statuses</option>
          {STATUSES.map((status) => (
            <option key={status} value={status}>
              {status.replace('_', ' ')}
            </option>
          ))}
        </select>
        <select
          value={filters.priority ?? ''}
          onChange={(e) => setFilters({ ...filters, priority: e.target.value as Priority | '' })}
          aria-label="Filter by priority"
          style={control}
        >
          <option value="">All priorities</option>
          {PRIORITIES.map((priority) => (
            <option key={priority} value={priority}>
              {priority}
            </option>
          ))}
        </select>
        <select
          value={filters.slaStatus ?? ''}
          onChange={(e) => setFilters({ ...filters, slaStatus: e.target.value as SlaStatus | '' })}
          aria-label="Filter by SLA status"
          style={control}
        >
          <option value="">All SLA states</option>
          {SLA_STATUSES.map((status) => (
            <option key={status} value={status}>
              {status.replace('_', ' ')}
            </option>
          ))}
        </select>
        {filtering && (
          <Button type="button" variant="secondary" onClick={() => setFilters({})}>
            Clear filters
          </Button>
        )}
      </form>

      <Card title="My Incidents" subtitle="Everything you have reported">
        {incidents === null && !error && <TableSkeleton rows={5} columns={6} />}

        {incidents !== null && incidents.length === 0 && (
          <EmptyState
            title={filtering ? 'No incidents match' : 'No incidents found'}
            description={
              filtering
                ? 'Try a different search, status, priority or SLA state.'
                : 'When something breaks, report it here and the support team will pick it up.'
            }
            action={
              filtering ? (
                <Button variant="secondary" onClick={() => setFilters({})}>
                  Clear filters
                </Button>
              ) : (
                <Button onClick={() => navigate('/employee/new')}>Create incident</Button>
              )
            }
          />
        )}

        {incidents !== null && incidents.length > 0 && (
          <>
            <IncidentTable incidents={incidents} />
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
