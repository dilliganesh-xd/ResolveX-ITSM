import type { ReactNode } from 'react';
import { tone, type ToneName, radius } from '@/lib/tokens';

interface StatusPillProps {
  label: string;
  toneName: ToneName;
  /**
   * Drawn in place of the dot, for states that carry a shape of their own —
   * a tick, a cross, a level meter. Keeps meaning off colour alone.
   */
  icon?: ReactNode;
}

/**
 * A pill takes a tone name, never a colour, so the same state cannot render
 * green on one screen and grey on another.
 */
export function StatusPill({ label, toneName, icon }: StatusPillProps) {
  const t = tone[toneName];
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        fontSize: 11,
        fontWeight: 700,
        padding: '3px 9px',
        borderRadius: radius.pill,
        whiteSpace: 'nowrap',
        background: t.bg,
        color: t.fg,
      }}
    >
      {icon ?? (
        <i
          style={{
            width: 6,
            height: 6,
            borderRadius: '50%',
            flexShrink: 0,
            background: t.dot,
          }}
        />
      )}
      {label}
    </span>
  );
}
