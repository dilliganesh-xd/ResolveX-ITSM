import { Button } from './Button';
import { color, font, radius, size } from '@/lib/tokens';
import type { Team } from '@/api/admin';
import type { IncidentFilters, IncidentStatus, Priority, SlaStatus } from '@/lib/types';

const STATUSES: IncidentStatus[] = ['NEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];
const PRIORITIES: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const SLA_STATUSES: SlaStatus[] = ['ON_TRACK', 'AT_RISK', 'BREACHED', 'COMPLETED'];

interface IncidentFilterBarProps {
  filters: IncidentFilters;
  teams: Team[];
  onChange: (filters: IncidentFilters) => void;
}

/**
 * Filters are applied server-side; this only holds the selection. Search is
 * submitted rather than applied per keystroke, so typing does not fire a request
 * per character.
 */
export function IncidentFilterBar({ filters, teams, onChange }: IncidentFilterBarProps) {
  const active = Boolean(
    filters.status || filters.priority || filters.teamId || filters.slaStatus || filters.search,
  );

  return (
    <form
      onSubmit={(event) => event.preventDefault()}
      style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center', marginBottom: 14 }}
    >
      <input
        type="search"
        placeholder="Search id, title, reporter"
        value={filters.search ?? ''}
        onChange={(e) => onChange({ ...filters, search: e.target.value })}
        style={{ ...controlStyle, flex: '0 1 240px' }}
        aria-label="Search incidents"
      />

      <select
        value={filters.status ?? ''}
        onChange={(e) => onChange({ ...filters, status: e.target.value as IncidentStatus | '' })}
        style={controlStyle}
        aria-label="Filter by status"
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
        onChange={(e) => onChange({ ...filters, priority: e.target.value as Priority | '' })}
        style={controlStyle}
        aria-label="Filter by priority"
      >
        <option value="">All priorities</option>
        {PRIORITIES.map((priority) => (
          <option key={priority} value={priority}>
            {priority}
          </option>
        ))}
      </select>

      <select
        value={filters.teamId ?? ''}
        onChange={(e) => onChange({ ...filters, teamId: e.target.value })}
        style={controlStyle}
        aria-label="Filter by team"
      >
        <option value="">All teams</option>
        {teams.map((team) => (
          <option key={team.id} value={team.id}>
            {team.name}
          </option>
        ))}
      </select>

      <select
        value={filters.slaStatus ?? ''}
        onChange={(e) => onChange({ ...filters, slaStatus: e.target.value as SlaStatus | '' })}
        style={controlStyle}
        aria-label="Filter by SLA status"
      >
        <option value="">All SLA states</option>
        {SLA_STATUSES.map((status) => (
          <option key={status} value={status}>
            {status.replace('_', ' ')}
          </option>
        ))}
      </select>

      {active && (
        <Button type="button" variant="secondary" onClick={() => onChange({})}>
          Clear filters
        </Button>
      )}
    </form>
  );
}

const controlStyle = {
  height: size.control,
  padding: '0 11px',
  border: `1px solid ${color.borderInput}`,
  borderRadius: radius.control,
  fontSize: 13,
  fontFamily: font.sans,
  background: color.surface,
  color: color.heading,
} as const;
