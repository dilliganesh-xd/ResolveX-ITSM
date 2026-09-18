import type { ComponentType, CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import { IconArrowRight, type IconProps } from '@/components/ui/icons';
import { color, font, radius, tone, type ToneName } from '@/lib/tokens';

interface StatCardProps {
  label: string;
  value?: number;
  /** Tints the card, but only when the figure is non-zero. */
  toneName?: ToneName;
  hint?: string;
  /** Sits in a tinted tile above the figure. Decorative — the label carries the meaning. */
  icon?: ComponentType<IconProps>;
  /**
   * Makes the card a link to the list this figure counts. A number on a
   * dashboard is a question; this is the answer being one click away.
   */
  to?: string;
  /** Overrides the figure, for stats that are not a plain count (e.g. "13%"). */
  display?: string;
}

/**
 * One figure with its label. The single stat card the whole product uses, so a
 * count looks the same on every dashboard.
 *
 * <p>The figure comes before the label, because the number is what the eye is
 * hunting for and the label is what confirms it. The icon is a landmark for
 * scanning a row of eight tiles; it is never the only thing naming the figure.
 *
 * <p>A tinted card stays neutral at zero: nothing breached is not a warning, and
 * a wall of red at rest teaches people to ignore red.
 */
export function StatCard({ label, value, toneName, hint, icon: Icon, to, display }: StatCardProps) {
  const active = toneName !== undefined && value !== undefined && value > 0;
  const t = toneName ? tone[toneName] : undefined;

  const body = (
    <>
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between' }}>
        {Icon && (
          <span
            aria-hidden
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: 30,
              height: 30,
              marginBottom: 10,
              borderRadius: radius.small,
              background: active && t ? 'rgba(255,255,255,.65)' : color.brandSoft,
              color: active && t ? t.fg : color.brandDark,
            }}
          >
            <Icon size={16} />
          </span>
        )}
        {to && (
          <span aria-hidden className="stat-card-arrow" style={arrowStyle}>
            <IconArrowRight size={14} />
          </span>
        )}
      </div>

      <div
        style={{
          fontFamily: font.mono,
          fontSize: 23,
          fontWeight: 600,
          lineHeight: 1.1,
          color: active && t ? t.fg : color.heading,
          fontVariantNumeric: 'tabular-nums',
        }}
      >
        {/* Two dashes until the API answers - a zero would be a claim. */}
        {display ?? value ?? '--'}
      </div>

      <div
        style={{
          marginTop: 5,
          fontSize: 11.5,
          fontWeight: 700,
          color: active && t ? t.fg : color.subtle,
        }}
      >
        {label}
      </div>

      {hint && (
        <div style={{ fontSize: 11, color: color.faint, fontWeight: 500, marginTop: 2 }}>{hint}</div>
      )}
    </>
  );

  const surface = {
    display: 'block',
    background: active && t ? t.bg : color.surface,
    border: `1px solid ${active && t ? t.line : color.border}`,
    borderRadius: radius.card,
    padding: '14px 16px',
    textDecoration: 'none',
  } as const;

  if (!to) {
    return <div style={surface}>{body}</div>;
  }

  return (
    <Link to={to} className="stat-card-link" style={surface}>
      {body}
    </Link>
  );
}

const arrowStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  width: 22,
  height: 22,
  borderRadius: radius.small,
  color: color.faint,
  opacity: 0,
  transition: 'opacity 150ms ease-out, transform 150ms ease-out',
};
