import { color, radius } from '@/lib/tokens';

/** A message and a way forward. An empty state that only says "no data" is incomplete. */
export function EmptyState({ title, description, action }: { title: string; description: string; action?: React.ReactNode }) {
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        textAlign: 'center',
        padding: '36px 24px',
      }}
    >
      <div
        style={{
          width: 52,
          height: 52,
          borderRadius: radius.card,
          background: '#F6F8FB',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          marginBottom: 14,
        }}
      >
        <svg width="23" height="23" viewBox="0 0 24 24" fill="none" stroke={color.faint}
             strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
          <path d="M9 13h6M9 17h6M9 9h1M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z" />
        </svg>
      </div>
      <div style={{ fontSize: 14.5, fontWeight: 800, color: color.body }}>{title}</div>
      <p style={{ fontSize: 12.5, color: color.subtle, fontWeight: 500, margin: '5px 0 0', maxWidth: 340 }}>
        {description}
      </p>
      {action && <div style={{ marginTop: 16 }}>{action}</div>}
    </div>
  );
}
