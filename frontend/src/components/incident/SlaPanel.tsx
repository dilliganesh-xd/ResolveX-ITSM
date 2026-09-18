import { Card } from '@/components/ui/Card';
import { StatusPill } from '@/components/ui/StatusPill';
import { SLA_TONE, describeStage, formatDateTime, humanise } from '@/lib/display';
import { IconAlert, IconCheck, IconClock } from '@/components/ui/icons';
import { color, font } from '@/lib/tokens';
import type { IncidentDetail, SlaStatus } from '@/lib/types';

interface SlaPanelProps {
  incident: IncidentDetail;
  /** True for agents and admins, who also see the response stage. */
  showResponseStage: boolean;
}

/**
 * The SLA panel.
 *
 * <p>Every verdict here was decided by the server. This component turns a
 * verdict and a deadline into a sentence - "Resolution SLA breached 35m ago" -
 * and never works out for itself whether something is late.
 *
 * <p>An employee sees the status and when resolution is due. The response-stage
 * fields are omitted from their copy of the incident entirely, so there is
 * nothing here to hide.
 */
export function SlaPanel({ incident, showResponseStage }: SlaPanelProps) {
  return (
    <Card
      title="SLA"
      subtitle={incident.slaPolicyName ?? 'No policy applied'}
      action={
        incident.slaStatus ? (
          <StatusPill
            label={humanise(incident.slaStatus)}
            toneName={SLA_TONE[incident.slaStatus]}
            icon={slaIcon(incident.slaStatus)}
          />
        ) : undefined
      }
    >
      {!incident.slaStatus && (
        <p style={{ margin: '0 0 10px', fontSize: 12, color: color.muted, fontWeight: 600 }}>
          No active policy existed for this priority when the incident was created, so it carries no
          SLA.
        </p>
      )}

      <div style={{ display: 'grid', gap: 12 }}>
        {showResponseStage && incident.responseSlaStatus && (
          <Stage
            label="Response"
            status={incident.responseSlaStatus}
            deadline={incident.responseDeadline}
            completedAt={incident.responseCompletedAt}
          />
        )}

        {incident.resolutionSlaStatus && (
          <Stage
            label="Resolution"
            status={incident.resolutionSlaStatus}
            deadline={incident.resolutionDeadline}
            completedAt={incident.resolutionCompletedAt}
          />
        )}
      </div>
    </Card>
  );
}

function Stage({
  label,
  status,
  deadline,
  completedAt,
}: {
  label: string;
  status: SlaStatus;
  deadline?: string;
  completedAt?: string;
}) {
  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
        <span style={{ fontSize: 11.5, fontWeight: 700, color: color.subtle }}>{label}</span>
        <StatusPill label={humanise(status)} toneName={SLA_TONE[status]} icon={slaIcon(status)} />
      </div>

      <div
        style={{
          fontSize: 12.5,
          fontWeight: 600,
          marginTop: 4,
          color: status === 'BREACHED' ? '#BE123C' : status === 'AT_RISK' ? '#B45309' : color.body,
        }}
      >
        {describeStage(label, status, deadline)}
      </div>

      <div style={{ fontFamily: font.mono, fontSize: 10.5, color: color.faint, marginTop: 2 }}>
        Due {formatDateTime(deadline)}
        {completedAt ? ` · met ${formatDateTime(completedAt)}` : ''}
      </div>
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
