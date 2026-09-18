import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getReport, type ReportSummary } from '@/api/admin';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { PageHeader } from '@/components/ui/PageHeader';
import { StatCard } from '@/components/ui/StatCard';
import { SectionLabel } from '@/components/ui/Toolbar';
import {
  IconAlert,
  IconCheck,
  IconClock,
  IconQueue,
  IconShield,
  IconTeam,
  IconTicket,
  IconUsers,
} from '@/components/ui/icons';
import { color, font, tone } from '@/lib/tokens';

export function AdminDashboard() {
  const navigate = useNavigate();
  const [report, setReport] = useState<ReportSummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getReport()
      .then(setReport)
      .catch(() => setError('Could not load reports.'));
  }, []);

  // How much of the estate is still inside its SLA. Undefined rather than 100
  // while the report loads, so the figure never claims a score it has not
  // measured; 100 when there is genuinely nothing to breach.
  const total = report?.totalIncidents;
  const breached = report?.breachedIncidents;
  const compliance =
    total === undefined || breached === undefined
      ? undefined
      : total === 0
        ? 100
        : Math.round(((total - breached) / total) * 100);

  // Compliance is the one figure worth colouring by how bad it is, rather than
  // by whether it is non-zero.
  const complianceTone = compliance === undefined ? undefined : compliance < 80 ? 'red' : compliance < 95 ? 'amber' : 'green';

  return (
    <>
      <PageHeader
        title="Dashboard"
        subtitle="Incident volume, SLA health and the shape of the organisation"
        action={
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            <Button variant="secondary" onClick={() => navigate('/admin/users')}>
              Create user
            </Button>
            <Button variant="secondary" onClick={() => navigate('/admin/teams')}>
              Create team
            </Button>
            <Button variant="secondary" onClick={() => navigate('/admin/sla-policies')}>
              SLA policies
            </Button>
            <Button onClick={() => navigate('/admin/reports')}>View reports</Button>
          </div>
        }
      />

      {error && (
        <div role="alert" style={{ color: tone.red.fg, fontWeight: 600, marginBottom: 12 }}>
          {error}
        </div>
      )}

      {/* Every tile below links to the list it counts, so a figure that looks
          wrong can be interrogated rather than just noted. */}
      <SectionLabel hint="Click any figure to see the incidents behind it">Service health</SectionLabel>
      <div style={gridStyle}>
        <StatCard
          label="Within SLA"
          display={compliance === undefined ? undefined : `${compliance}%`}
          value={compliance}
          toneName={complianceTone}
          hint={
            total !== undefined && breached !== undefined ? `${total - breached} of ${total}` : undefined
          }
          icon={IconShield}
        />
        <StatCard
          label="SLA breached"
          value={report?.breachedIncidents}
          toneName="red"
          icon={IconAlert}
          to="/admin/incidents?slaStatus=BREACHED"
        />
        <StatCard
          label="SLA at risk"
          value={report?.atRiskIncidents}
          toneName="amber"
          icon={IconClock}
          to="/admin/incidents?slaStatus=AT_RISK"
        />
        <StatCard
          label="Open incidents"
          value={report?.openIncidents}
          icon={IconQueue}
          to="/admin/incidents?status=NEW"
        />
      </div>

      <SectionLabel hint="Across every team">Incidents</SectionLabel>
      <div style={gridStyle}>
        <StatCard label="Total" value={report?.totalIncidents} icon={IconTicket} to="/admin/incidents" />
        <StatCard
          label="In progress"
          value={report?.inProgressIncidents}
          icon={IconClock}
          to="/admin/incidents?status=IN_PROGRESS"
        />
        <StatCard
          label="Resolved"
          value={report?.resolvedIncidents}
          icon={IconCheck}
          to="/admin/incidents?status=RESOLVED"
        />
        <StatCard
          label="Closed"
          value={report?.closedIncidents}
          icon={IconCheck}
          to="/admin/incidents?status=CLOSED"
        />
      </div>

      <SectionLabel hint="People and structure">Organisation</SectionLabel>
      <div style={gridStyle}>
        <StatCard label="Total users" value={report?.totalUsers} icon={IconUsers} to="/admin/users" />
        <StatCard label="Active users" value={report?.activeUsers} icon={IconUsers} to="/admin/users" />
        <StatCard label="Teams" value={report?.totalTeams} icon={IconTeam} to="/admin/teams" />
      </div>

      <SectionLabel hint="Where the work sits">Breakdown</SectionLabel>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 12 }}>
        <Card title="By status" subtitle="All incidents">
          <Breakdown data={report?.byStatus} />
        </Card>
        <Card title="By priority" subtitle="All incidents">
          <Breakdown data={report?.byPriority} />
        </Card>
        <Card title="By team" subtitle="Including unrouted">
          <Breakdown data={report?.byTeam} />
        </Card>
      </div>
    </>
  );
}

/**
 * auto-fill rather than auto-fit: a row of three tiles keeps its tile width and
 * leaves the remainder empty, instead of stretching three cards across 1400px
 * so each holds one digit in an acre of white.
 */
const gridStyle = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fill, minmax(190px, 1fr))',
  gap: 12,
} as const;

/** A bar per row, sized against the largest value - no chart library needed. */
function Breakdown({ data }: { data?: Record<string, number> }) {
  if (!data) {
    return <div style={{ color: color.muted, fontWeight: 600 }}>Loading...</div>;
  }
  const entries = Object.entries(data);
  const largest = Math.max(1, ...entries.map(([, count]) => count));

  return (
    <div style={{ display: 'grid', gap: 9 }}>
      {entries.map(([key, count]) => (
        <div key={key}>
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, marginBottom: 3 }}>
            <span style={{ fontWeight: 600, color: color.body, fontSize: 12 }}>{key.replace(/_/g, ' ')}</span>
            <span
              style={{
                fontFamily: font.mono,
                fontSize: 12,
                color: color.heading,
                fontVariantNumeric: 'tabular-nums',
              }}
            >
              {count}
            </span>
          </div>
          <div style={{ height: 6, borderRadius: 3, background: color.hairline, overflow: 'hidden' }}>
            <div
              style={{
                height: '100%',
                width: `${Math.round((count / largest) * 100)}%`,
                background: count > 0 ? color.brand : 'transparent',
              }}
            />
          </div>
        </div>
      ))}
    </div>
  );
}
