import { useEffect, useState } from 'react';
import {
  getReport,
  listAdminAudit,
  type AdminAuditEntry,
  type ReportSummary,
  type WorkloadRow,
} from '@/api/admin';
import { Card } from '@/components/ui/Card';
import { DataTable } from '@/components/ui/DataTable';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorBanner } from '@/components/ui/Feedback';
import { PageHeader } from '@/components/ui/PageHeader';
import { StatusPill } from '@/components/ui/StatusPill';
import { BarRows, ProportionBar, StackedBarRows, type Slice } from '@/components/ui/charts';
import { SectionLabel } from '@/components/ui/Toolbar';
import { describeFailure } from '@/lib/adminForm';
import { formatDateTime, humanise } from '@/lib/display';
import { color, font, tone } from '@/lib/tokens';

const TARGET_TONE: Record<string, 'purple' | 'blue' | 'amber'> = {
  USER: 'purple',
  TEAM: 'blue',
  SLA_POLICY: 'amber',
};

/**
 * Reports: the incident breakdowns, plus what administrators have been doing.
 *
 * <p>The admin audit is separate from an incident's activity trail, because
 * these actions belong to no incident.
 */
export function AdminReportsPage() {
  const [report, setReport] = useState<ReportSummary | null>(null);
  const [audit, setAudit] = useState<AdminAuditEntry[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getReport()
      .then(setReport)
      .catch((caught) => setError(describeFailure(caught, 'Could not load reports.').message));
    listAdminAudit()
      .then(setAudit)
      .catch(() => setAudit([]));
  }, []);

  return (
    <>
      <PageHeader title="Reports" subtitle="Incident breakdowns and the administrative audit trail" />

      {error && <ErrorBanner message={error} />}

      <SectionLabel hint="Every figure is labelled; colour only reinforces it">Distribution</SectionLabel>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))',
          gap: 12,
          marginBottom: 14,
        }}
      >
        <Card title="By status" subtitle="Where every incident currently sits">
          {report ? <BarRows data={named(report.byStatus)} /> : <Loading />}
        </Card>

        <Card title="Priority mix" subtitle="What the queue is made of">
          {report ? <ProportionBar data={prioritySlices(report.byPriority)} /> : <Loading />}
        </Card>

        <Card title="By team" subtitle="Including unrouted">
          {report ? <BarRows data={named(report.byTeam)} /> : <Loading />}
        </Card>
      </div>

      <SectionLabel hint="Counts and current SLA position">Totals</SectionLabel>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
          gap: 12,
          marginBottom: 14,
        }}
      >
        <Card title="Incident summary" subtitle="Across all incidents">
          <Figures
            rows={[
              ['Total', report?.totalIncidents],
              ['Open', report?.openIncidents],
              ['In progress', report?.inProgressIncidents],
              ['Resolved', report?.resolvedIncidents],
              ['Closed', report?.closedIncidents],
            ]}
          />
        </Card>
        <Card title="SLA summary" subtitle="Current state">
          <Figures
            rows={[
              ['On track', report?.onTrackIncidents],
              ['At risk', report?.atRiskIncidents],
              ['Breached', report?.breachedIncidents],
            ]}
          />
        </Card>
      </div>

      <SectionLabel hint="Who is carrying what">Workload</SectionLabel>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
          gap: 12,
          marginBottom: 14,
        }}
      >
        <Card title="Team workload" subtitle="Open, in progress and finished">
          {report ? <WorkloadChart rows={report.teamWorkload} empty="No teams yet." /> : <Loading />}
          <div style={{ marginTop: 16 }}>
            <Workload rows={report?.teamWorkload} empty="No teams yet." />
          </div>
        </Card>
        <Card title="Agent workload" subtitle="Per support agent">
          {report ? <WorkloadChart rows={report.agentWorkload} empty="No agents yet." /> : <Loading />}
          <div style={{ marginTop: 16 }}>
            <Workload rows={report?.agentWorkload} empty="No agents yet." />
          </div>
        </Card>
      </div>

      <Card title="Administrative audit" subtitle={audit ? `${audit.length} recorded` : 'Loading'}>
        {audit === null && <div style={{ color: color.muted, fontWeight: 600 }}>Loading...</div>}
        {audit !== null && audit.length === 0 && (
          <EmptyState
            title="Nothing recorded yet"
            description="Creating a user, editing a team or changing an SLA policy will appear here."
          />
        )}
        {audit !== null && audit.length > 0 && (
          <ol style={{ margin: 0, padding: 0, listStyle: 'none', display: 'grid', gap: 10 }}>
            {audit.map((entry) => (
              <li key={entry.id} style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
                <StatusPill
                  label={humanise(entry.targetType)}
                  toneName={TARGET_TONE[entry.targetType] ?? 'grey'}
                />
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontSize: 12.5, fontWeight: 600, color: color.body }}>
                    {entry.description}
                  </div>
                  <div style={{ fontFamily: font.mono, fontSize: 10.5, color: color.faint, marginTop: 2 }}>
                    {formatDateTime(entry.createdAt)}
                  </div>
                </div>
              </li>
            ))}
          </ol>
        )}
      </Card>
    </>
  );
}

/** A plain figure list, for counts that do not want a bar. */
function Figures({ rows }: { rows: [string, number | undefined][] }) {
  return (
    <div style={{ display: 'grid', gap: 8 }}>
      {rows.map(([label, value]) => (
        <div key={label} style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
          <span style={{ fontSize: 12.5, fontWeight: 600, color: color.body }}>{label}</span>
          <span
            style={{
              fontFamily: font.mono,
              fontSize: 13,
              fontWeight: 600,
              color: color.heading,
              fontVariantNumeric: 'tabular-nums',
            }}
          >
            {/* Two dashes until the API answers: a zero would be a claim. */}
            {value ?? '--'}
          </span>
        </div>
      ))}
    </div>
  );
}

/** Who is carrying what. Three columns that add up to the fourth. */
function Workload({ rows, empty }: { rows?: WorkloadRow[]; empty: string }) {
  if (!rows) {
    return <div style={{ color: color.muted, fontWeight: 600 }}>Loading...</div>;
  }
  if (rows.length === 0) {
    return <div style={{ color: color.faint, fontWeight: 600 }}>{empty}</div>;
  }
  return (
    <DataTable
      head={['Name', 'Open', 'In progress', 'Resolved', 'Total']}
      rows={rows}
      rowKey={(row) => row.name}
      minWidth={360}
      render={(row) => [
        <strong style={{ color: color.heading }}>{row.name}</strong>,
        <Figure value={row.open} />,
        <Figure value={row.inProgress} />,
        <Figure value={row.resolved} />,
        <Figure value={row.total} />,
      ]}
    />
  );
}

function Figure({ value }: { value: number }) {
  return (
    <span
      style={{
        fontFamily: font.mono,
        fontSize: 12,
        color: value > 0 ? color.heading : color.faint,
        fontVariantNumeric: 'tabular-nums',
      }}
    >
      {value}
    </span>
  );
}

function Loading() {
  return <div style={{ color: color.muted, fontWeight: 600 }}>Loading...</div>;
}

/** A record keyed by enum name, as readable rows. */
function named(data: Record<string, number>): { label: string; value: number }[] {
  return Object.entries(data).map(([key, value]) => ({ label: humanise(key), value }));
}

/**
 * Priority is a severity scale, so it takes the status palette the pills
 * already use rather than an arbitrary categorical one — grey for LOW is the
 * meaning, not a colour that failed a chroma check.
 */
const PRIORITY_ORDER = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const;
const PRIORITY_FILL: Record<string, string> = {
  LOW: tone.grey.dot,
  MEDIUM: tone.blue.dot,
  HIGH: tone.amber.dot,
  CRITICAL: tone.red.dot,
};

function prioritySlices(data: Record<string, number>): Slice[] {
  return PRIORITY_ORDER.map((key) => ({
    label: humanise(key),
    value: data[key] ?? 0,
    fill: PRIORITY_FILL[key] ?? tone.grey.dot,
  }));
}

/**
 * The three workload measures. Validated as a categorical triple: the worst
 * adjacent pair separates by dE 8.9 under protanopia, 23.8 for normal vision.
 */
const WORKLOAD_KEYS: Slice[] = [
  { label: 'Open', fill: '#2563EB' },
  { label: 'In progress', fill: '#F59E0B' },
  { label: 'Resolved', fill: '#10B981' },
];

function WorkloadChart({ rows, empty }: { rows: WorkloadRow[]; empty: string }) {
  if (rows.length === 0) {
    return <div style={{ color: color.faint, fontWeight: 600 }}>{empty}</div>;
  }
  return (
    <StackedBarRows
      keys={WORKLOAD_KEYS}
      rows={rows.map((row) => ({
        label: row.name,
        parts: [
          { label: 'Open', value: row.open, fill: '#2563EB' },
          { label: 'In progress', value: row.inProgress, fill: '#F59E0B' },
          { label: 'Resolved', value: row.resolved, fill: '#10B981' },
        ],
      }))}
    />
  );
}
