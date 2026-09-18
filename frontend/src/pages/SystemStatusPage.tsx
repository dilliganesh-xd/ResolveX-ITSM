import { useEffect, useState } from 'react';
import { Card } from '@/components/ui/Card';
import { StatusPill } from '@/components/ui/StatusPill';
import { fetchHealth, type HealthResponse } from '@/api/health';
import { color, font, size } from '@/lib/tokens';

type Phase = 'loading' | 'ok' | 'error';

/**
 * Phase 1's only screen: it proves the frontend, the proxy and the backend are
 * genuinely talking to each other. It is replaced by the role dashboards in
 * Phase 2 and is not part of the V1 feature set.
 */
export function SystemStatusPage() {
  const [phase, setPhase] = useState<Phase>('loading');
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [message, setMessage] = useState<string>('');

  useEffect(() => {
    let cancelled = false;
    fetchHealth()
      .then((data) => {
        if (cancelled) return;
        setHealth(data);
        setPhase('ok');
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        setMessage(error instanceof Error ? error.message : 'Unknown error');
        setPhase('error');
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <main style={{ padding: size.pagePadding, maxWidth: 720 }}>
      <h1
        style={{
          margin: '0 0 4px',
          fontSize: 20,
          fontWeight: 800,
          letterSpacing: '-0.4px',
          color: color.heading,
        }}
      >
        System Status
      </h1>
      <p style={{ margin: '0 0 18px', fontSize: 12.5, fontWeight: 500, color: color.muted }}>
        Phase 1 scaffold — confirms the React app reaches the Spring Boot API through the dev proxy.
      </p>

      <Card
        title="Backend connectivity"
        subtitle="GET /api/health"
        action={
          phase === 'loading' ? (
            <StatusPill label="Checking" toneName="blue" />
          ) : phase === 'ok' ? (
            <StatusPill label="Reachable" toneName="green" />
          ) : (
            <StatusPill label="Unreachable" toneName="red" />
          )
        }
      >
        {phase === 'loading' && <SkeletonLines />}

        {phase === 'ok' && health && (
          <dl style={{ margin: 0, display: 'grid', gap: 10 }}>
            <Row label="Status" value={health.status} />
            <Row label="Application" value={health.application} />
            <Row label="Version" value={health.version} />
            <Row label="Checked at" value={new Date(health.timestamp).toLocaleString()} />
          </dl>
        )}

        {phase === 'error' && (
          <div>
            <div style={{ fontWeight: 700, color: color.heading, fontSize: 13 }}>
              The API did not respond
            </div>
            <p style={{ margin: '6px 0 0', color: color.muted, fontSize: 12.5 }}>
              Start the backend with <code style={{ fontFamily: font.mono }}>mvn spring-boot:run</code>{' '}
              from <code style={{ fontFamily: font.mono }}>backend/</code>, then reload. Details:{' '}
              {message}
            </p>
          </div>
        )}
      </Card>
    </main>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ display: 'flex', gap: 12, alignItems: 'baseline' }}>
      <dt style={{ minWidth: 104, fontSize: 11.5, fontWeight: 700, color: color.subtle }}>{label}</dt>
      <dd
        style={{
          margin: 0,
          fontFamily: font.mono,
          fontSize: 12,
          color: color.heading,
          fontVariantNumeric: 'tabular-nums',
        }}
      >
        {value}
      </dd>
    </div>
  );
}

/** Skeletons match the real row rhythm so nothing jumps when data lands. */
function SkeletonLines() {
  return (
    <div style={{ display: 'grid', gap: 11 }}>
      {['70%', '85%', '45%'].map((width) => (
        <span
          key={width}
          style={{
            display: 'block',
            width,
            height: 11,
            borderRadius: 6,
            background: '#EEF1F5',
          }}
        />
      ))}
    </div>
  );
}
