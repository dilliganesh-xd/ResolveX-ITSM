import { color } from '@/lib/tokens';

export function PageHeader({ title, subtitle, action }: { title: string; subtitle?: string; action?: React.ReactNode }) {
  return (
    <header
      style={{
        display: 'flex',
        alignItems: 'flex-start',
        justifyContent: 'space-between',
        gap: 12,
        marginBottom: 18,
      }}
    >
      <div>
        <h1 style={{ margin: 0, fontSize: 20, fontWeight: 800, letterSpacing: '-0.4px', color: color.heading }}>
          {title}
        </h1>
        {subtitle && (
          <p style={{ margin: '4px 0 0', fontSize: 12.5, fontWeight: 500, color: color.muted }}>{subtitle}</p>
        )}
      </div>
      {action}
    </header>
  );
}
