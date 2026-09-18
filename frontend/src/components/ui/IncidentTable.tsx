import { Link } from 'react-router-dom';
import { StatusPill } from './StatusPill';
import { PriorityLevel } from './PriorityLevel';
import { IconAlert, IconArrowRight, IconCheck, IconClock } from './icons';
import {
  PRIORITY_TONE,
  SLA_TONE,
  STATUS_TONE,
  formatDateTime,
  formatDeadline,
  humanise,
} from '@/lib/display';
import { color, font, size, tone } from '@/lib/tokens';
import type { IncidentSummary, SlaStatus } from '@/lib/types';

interface IncidentTableProps {
  incidents: IncidentSummary[];
  /** Columns the employee view has no use for, including the response deadline. */
  showQueueColumns?: boolean;
}

/**
 * The incident list, used by every role. The reference is the link into the
 * detail page; the whole row is not clickable, so text stays selectable.
 *
 * <p>A breached row carries a red left edge and a tinted background as well as
 * its pill, so a queue can be scanned for trouble without reading every cell.
 */
export function IncidentTable({ incidents, showQueueColumns = false }: IncidentTableProps) {
  // position: relative so absolutely-positioned descendants (the sr-only
  // header label) resolve against this box and are clipped by its scroll,
  // instead of against the page and widening it.
  return (
    <div style={{ overflowX: 'auto', position: 'relative' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: showQueueColumns ? 1120 : 720 }}>
        <thead>
          <tr style={{ background: color.thead }}>
            <Th>Incident ID</Th>
            <Th>Title</Th>
            <Th>Priority</Th>
            <Th>Status</Th>
            {showQueueColumns && <Th>Reporter</Th>}
            {showQueueColumns && <Th>Team</Th>}
            <Th>Agent</Th>
            <Th>Created</Th>
            <Th>SLA</Th>
            {showQueueColumns && <Th>Response due</Th>}
            <Th>Resolution due</Th>
            <Th>
              <span className="sr-only">Open</span>
            </Th>
          </tr>
        </thead>
        <tbody>
          {incidents.map((incident) => {
            const breached = incident.slaStatus === 'BREACHED';
            const settled = incident.status === 'RESOLVED' || incident.status === 'CLOSED';
            // A clock the server has already called COMPLETED has stopped, even
            // while the incident is still open: counting down to a response that
            // was given tells an agent they are late for something they did. A
            // stage the server calls BREACHED keeps running, because how far past
            // the deadline it is still matters.
            const responseSettled = settled || incident.responseSlaStatus === 'COMPLETED';
            const resolutionSettled = settled || incident.resolutionSlaStatus === 'COMPLETED';
            return (
              <tr
                className="data-row"
                key={incident.id}
                style={{ background: breached ? tone.red.bg : undefined }}
              >
                <Td accent={breached ? tone.red.dot : undefined}>
                  <Link
                    to={`/incidents/${incident.id}`}
                    style={{
                      fontFamily: font.mono,
                      fontSize: 12,
                      fontWeight: 600,
                      color: color.brand,
                      textDecoration: 'none',
                    }}
                  >
                    {incident.reference}
                  </Link>
                </Td>
                <Td>
                  <Link
                    to={`/incidents/${incident.id}`}
                    style={{ fontWeight: 700, color: color.heading, textDecoration: 'none' }}
                  >
                    {incident.title}
                  </Link>
                  <div style={{ fontSize: 11.5, color: color.faint, fontWeight: 600 }}>{incident.category}</div>
                </Td>
                <Td>
                  <StatusPill
                    label={incident.priority}
                    toneName={PRIORITY_TONE[incident.priority]}
                    icon={<PriorityLevel priority={incident.priority} />}
                  />
                </Td>
                <Td>
                  <StatusPill label={humanise(incident.status)} toneName={STATUS_TONE[incident.status]} />
                </Td>
                {showQueueColumns && <Td>{incident.reporterName}</Td>}
                {showQueueColumns && (
                  <Td>
                    <Muted value={incident.assignedTeamName} fallback="Unrouted" />
                  </Td>
                )}
                <Td>
                  <Muted value={incident.assignedAgentName} fallback="Unassigned" />
                </Td>
                <Td>
                  <Stamp>{formatDateTime(incident.createdAt)}</Stamp>
                </Td>
                <Td>
                  {incident.slaStatus ? (
                    // The icon carries the meaning as well as the tone, so the
                    // state is legible without relying on colour.
                    <StatusPill
                      label={humanise(incident.slaStatus)}
                      toneName={SLA_TONE[incident.slaStatus]}
                      icon={slaIcon(incident.slaStatus)}
                    />
                  ) : (
                    // Two dashes, not a zero: a zero would be a claim.
                    <span style={{ fontFamily: font.mono, color: color.faint }}>--</span>
                  )}
                </Td>
                {showQueueColumns && (
                  <Td>
                    <Deadline iso={incident.responseDeadline} settled={responseSettled} />
                  </Td>
                )}
                <Td>
                  <Deadline iso={incident.resolutionDeadline} settled={resolutionSettled} />
                </Td>
                <Td>
                  {/* The reference and title are already links; this is the
                      affordance that says the whole row leads somewhere. */}
                  <Link
                    to={`/incidents/${incident.id}`}
                    className="row-open"
                    aria-label={`Open ${incident.reference}`}
                    style={rowOpenStyle}
                  >
                    <IconArrowRight size={15} />
                  </Link>
                </Td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

/** One icon per SLA verdict, from the shared family. */
function slaIcon(status: SlaStatus) {
  switch (status) {
    case 'BREACHED':
      return <IconAlert size={12} />;
    case 'AT_RISK':
      return <IconClock size={12} />;
    default:
      return <IconCheck size={12} />;
  }
}

const rowOpenStyle = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  width: 28,
  height: 28,
  borderRadius: 7,
  color: color.faint,
  textDecoration: 'none',
} as const;

/** The date, with time remaining underneath while the clock is still running. */
function Deadline({ iso, settled }: { iso?: string; settled: boolean }) {
  if (!iso) {
    return <span style={{ fontFamily: font.mono, color: color.faint }}>--</span>;
  }
  const overdue = new Date(iso).getTime() < Date.now();
  return (
    <div>
      <Stamp>{formatDateTime(iso)}</Stamp>
      {!settled && (
        <div
          style={{
            fontFamily: font.mono,
            fontSize: 10.5,
            fontWeight: 600,
            color: overdue ? tone.red.fg : color.faint,
            marginTop: 2,
          }}
        >
          {formatDeadline(iso)}
        </div>
      )}
    </div>
  );
}

function Stamp({ children }: { children: React.ReactNode }) {
  return <span style={{ fontFamily: font.mono, fontSize: 11.5, color: color.subtle }}>{children}</span>;
}

function Muted({ value, fallback }: { value?: string; fallback: string }) {
  return <span style={{ color: value ? color.body : color.faint }}>{value ?? fallback}</span>;
}

function Th({ children }: { children: React.ReactNode }) {
  return (
    <th
      style={{
        fontFamily: font.mono,
        fontSize: 10.5,
        fontWeight: 600,
        color: color.tableHead,
        letterSpacing: '0.4px',
        textTransform: 'uppercase',
        padding: size.cellPadding,
        textAlign: 'left',
        whiteSpace: 'nowrap',
      }}
    >
      {children}
    </th>
  );
}

function Td({ children, accent }: { children: React.ReactNode; accent?: string }) {
  return (
    <td
      style={{
        padding: size.cellPadding,
        fontSize: 12.5,
        color: color.body,
        borderTop: `1px solid ${color.hairline}`,
        borderLeft: accent ? `3px solid ${accent}` : undefined,
        verticalAlign: 'top',
      }}
    >
      {children}
    </td>
  );
}
