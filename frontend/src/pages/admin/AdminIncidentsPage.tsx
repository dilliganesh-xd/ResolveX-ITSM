import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { listIncidents } from '@/api/incidents';
import { listTeams, type Team } from '@/api/admin';
import { Card } from '@/components/ui/Card';
import { Pagination } from '@/components/ui/Pagination';
import { EmptyState } from '@/components/ui/EmptyState';
import { IncidentFilterBar } from '@/components/ui/IncidentFilterBar';
import { IncidentTable } from '@/components/ui/IncidentTable';
import { PageHeader } from '@/components/ui/PageHeader';
import { humanise } from '@/lib/display';
import { font, radius } from '@/lib/tokens';
import { color } from '@/lib/tokens';
import type {
  IncidentFilters,
  IncidentStatus,
  IncidentSummary,
  Priority,
  SlaStatus,
} from '@/lib/types';

const PAGE_SIZE = 20;

const STATUSES: IncidentStatus[] = ['NEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];
const PRIORITIES: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const SLA_STATUSES: SlaStatus[] = ['ON_TRACK', 'AT_RISK', 'BREACHED', 'COMPLETED'];

/**
 * The filter selection lives in the URL.
 *
 * <p>That makes a filtered view a thing you can link to — the dashboard tiles
 * point straight at one — bookmark, and reach with the browser's Back button,
 * none of which work when the selection is only component state.
 *
 * <p>Values are checked against the enums rather than trusted: a hand-edited
 * query string should narrow to nothing sensible, not send a junk value to the
 * API.
 */
function readFilters(params: URLSearchParams): IncidentFilters {
  const pick = <T extends string>(key: string, allowed: T[]): T | undefined => {
    const raw = params.get(key);
    return raw && (allowed as string[]).includes(raw) ? (raw as T) : undefined;
  };
  return {
    status: pick('status', STATUSES),
    priority: pick('priority', PRIORITIES),
    slaStatus: pick('slaStatus', SLA_STATUSES),
    teamId: params.get('teamId') ?? undefined,
    search: params.get('search') ?? undefined,
  };
}

function writeFilters(filters: IncidentFilters): URLSearchParams {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) {
    if (value) {
      params.set(key, String(value));
    }
  }
  return params;
}

/** Every incident in the system. The admin route to viewing and closing one. */
export function AdminIncidentsPage() {
  const [incidents, setIncidents] = useState<IncidentSummary[] | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [teams, setTeams] = useState<Team[]>([]);
  const [error, setError] = useState<string | null>(null);

  const [searchParams, setSearchParams] = useSearchParams();
  const filters = readFilters(searchParams);

  // replace: changing a filter is a refinement of the same view, not a new
  // place, so it should not need a Back press per dropdown to escape.
  const setFilters = (next: IncidentFilters) => setSearchParams(writeFilters(next), { replace: true });

  const load = useCallback((applied: IncidentFilters, pageNumber: number) => {
    listIncidents(applied, pageNumber, PAGE_SIZE)
      .then((result) => {
        setIncidents(result.content);
        setPage(result.page);
        setTotalPages(result.totalPages);
        setTotalElements(result.totalElements);
        setError(null);
      })
      .catch(() => setError('Could not load incidents.'));
  }, []);

  // Keyed on the serialised query rather than the filters object, which is
  // rebuilt on every render and would otherwise refire the request each time.
  const query = searchParams.toString();
  useEffect(() => {
    const timer = setTimeout(() => load(readFilters(new URLSearchParams(query)), 0), 250);
    return () => clearTimeout(timer);
  }, [query, load]);

  useEffect(() => {
    listTeams().then(setTeams).catch(() => setTeams([]));
  }, []);

  const filtered = Boolean(
    filters.status || filters.priority || filters.teamId || filters.slaStatus || filters.search,
  );

  return (
    <>
      <PageHeader
        title="All Incidents"
        subtitle={incidents ? `${totalElements} incident${totalElements === 1 ? '' : 's'}` : 'Loading incidents'}
      />

      <IncidentFilterBar filters={filters} teams={teams} onChange={setFilters} />

      {/* What is currently narrowing the list, and how to undo it. Without
          this, a view reached from a dashboard tile looks like the whole
          table with a suspiciously small count. */}
      {filtered && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap', marginBottom: 14 }}>
          <span style={{ fontSize: 11.5, fontWeight: 700, color: color.subtle }}>Filtered by</span>
          {activeChips(filters, teams).map((chip) => (
            <span key={chip.key} className="filter-chip" style={chipStyle}>
              <span style={{ color: color.faint, fontWeight: 600 }}>{chip.field}</span>
              {chip.value}
              <button
                type="button"
                onClick={() => setFilters({ ...filters, [chip.key]: undefined })}
                aria-label={`Remove ${chip.field} filter`}
                style={chipCloseStyle}
              >
                &times;
              </button>
            </span>
          ))}
          <button type="button" onClick={() => setFilters({})} style={clearAllStyle}>
            Clear all
          </button>
        </div>
      )}

      <Card>
        {error && <div style={{ color: '#BE123C', fontWeight: 600 }}>{error}</div>}
        {incidents === null && !error && <div style={{ color: color.muted, fontWeight: 600 }}>Loading...</div>}
        {incidents !== null && incidents.length === 0 && (
          <EmptyState
            title={filtered ? 'No incidents match' : 'No incidents yet'}
            description={
              filtered
                ? 'Try a different status, priority, team or search term.'
                : 'Incidents appear here as employees report them.'
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

/** The applied filters, as removable chips. */
function activeChips(filters: IncidentFilters, teams: Team[]) {
  const chips: { key: keyof IncidentFilters; field: string; value: string }[] = [];
  if (filters.status) {
    chips.push({ key: 'status', field: 'Status', value: humanise(filters.status) });
  }
  if (filters.priority) {
    chips.push({ key: 'priority', field: 'Priority', value: filters.priority });
  }
  if (filters.slaStatus) {
    chips.push({ key: 'slaStatus', field: 'SLA', value: humanise(filters.slaStatus) });
  }
  if (filters.teamId) {
    const team = teams.find((candidate) => candidate.id === filters.teamId);
    chips.push({ key: 'teamId', field: 'Team', value: team?.name ?? 'Unknown team' });
  }
  if (filters.search) {
    chips.push({ key: 'search', field: 'Search', value: filters.search });
  }
  return chips;
}

const chipStyle = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 6,
  height: 26,
  padding: '0 4px 0 9px',
  borderRadius: radius.pill,
  background: color.brandSoft,
  color: color.brandDark,
  fontSize: 11.5,
  fontWeight: 700,
  whiteSpace: 'nowrap',
} as const;

const chipCloseStyle = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  width: 18,
  height: 18,
  border: 'none',
  borderRadius: '50%',
  background: 'transparent',
  color: color.brandDark,
  fontSize: 14,
  lineHeight: 1,
  cursor: 'pointer',
  fontFamily: font.sans,
} as const;

const clearAllStyle = {
  border: 'none',
  background: 'transparent',
  padding: '0 4px',
  fontFamily: font.sans,
  fontSize: 11.5,
  fontWeight: 700,
  color: color.subtle,
  textDecoration: 'underline',
  cursor: 'pointer',
} as const;
