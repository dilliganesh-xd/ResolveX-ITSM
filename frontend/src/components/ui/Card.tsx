import type { ReactNode } from 'react';
import { color, radius, shadow } from '@/lib/tokens';

interface CardProps {
  title?: string;
  subtitle?: string;
  action?: ReactNode;
  children: ReactNode;
}

/**
 * With a title a card gets a header rule and a 16px body; without one it is a
 * plain padded surface.
 */
export function Card({ title, subtitle, action, children }: CardProps) {
  return (
    <section
      style={{
        background: color.surface,
        border: `1px solid ${color.border}`,
        borderRadius: radius.card,
        boxShadow: shadow.card,
        overflow: 'hidden',
        width: '100%',
      }}
    >
      {title && (
        <header
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 10,
            padding: '14px 16px',
            borderBottom: `1px solid ${color.hairline}`,
          }}
        >
          <div>
            <div style={{ fontSize: 13.5, fontWeight: 800, color: color.heading }}>{title}</div>
            {subtitle && (
              <div style={{ fontSize: 11.5, fontWeight: 600, color: color.faint, marginTop: 2 }}>
                {subtitle}
              </div>
            )}
          </div>
          {action}
        </header>
      )}
      <div style={{ padding: 16, fontSize: 12.5, color: color.body }}>{children}</div>
    </section>
  );
}
