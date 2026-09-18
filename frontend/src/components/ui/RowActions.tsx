import type { ComponentType, ReactNode } from 'react';
import type { IconProps } from '@/components/ui/icons';
import { color, font, radius, tone } from '@/lib/tokens';

/**
 * The actions available on a table row, as icons.
 *
 * <p>Four labelled buttons per row ran to roughly 320px — a third of the table
 * spent restating the same four words on every line. Icons collapse that to
 * about 120px and give the eye a shape to aim at instead of a word to re-read.
 *
 * <p>The label does not disappear, it moves: each button carries it as its
 * accessible name and as a native tooltip, so the control is still named for a
 * screen reader, for the keyboard, and for anyone who hovers to check.
 */

export interface RowAction {
  label: string;
  icon: ComponentType<IconProps>;
  onClick: () => void;
  disabled?: boolean;
  /** 'danger' for the one that takes something away. */
  intent?: 'neutral' | 'danger';
}

export function RowActions({ actions }: { actions: RowAction[] }) {
  return (
    <div style={{ display: 'flex', gap: 2, justifyContent: 'flex-end' }}>
      {actions.map((action) => (
        <IconButton key={action.label} {...action} />
      ))}
    </div>
  );
}

export function IconButton({
  label,
  icon: Icon,
  onClick,
  disabled,
  intent = 'neutral',
}: RowAction) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      title={label}
      className={intent === 'danger' ? 'icon-button icon-button-danger' : 'icon-button'}
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: 30,
        height: 30,
        flexShrink: 0,
        border: 'none',
        borderRadius: radius.small,
        background: 'transparent',
        color: intent === 'danger' ? tone.red.fg : color.subtle,
        cursor: disabled ? 'not-allowed' : 'pointer',
        opacity: disabled ? 0.35 : 1,
        fontFamily: font.sans,
      }}
    >
      <Icon size={16} />
    </button>
  );
}

/**
 * A person, as a name with their initials beside it. Used wherever a table
 * lists people, so a human row is distinguishable from a record row at a
 * glance.
 */
export function PersonCell({
  name,
  secondary,
  note,
}: {
  name: string;
  secondary?: string;
  /** Appended to the name in a quieter weight, e.g. "(you)". */
  note?: ReactNode;
}) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 9, minWidth: 0 }}>
      <span aria-hidden style={avatarStyle}>
        {initials(name)}
      </span>
      <span style={{ minWidth: 0 }}>
        <span style={{ display: 'block', fontWeight: 700, color: color.heading }}>
          {name}
          {note}
        </span>
        {secondary && (
          <span style={{ display: 'block', fontFamily: font.mono, fontSize: 11, color: color.faint }}>
            {secondary}
          </span>
        )}
      </span>
    </div>
  );
}

const avatarStyle = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  width: 30,
  height: 30,
  flexShrink: 0,
  borderRadius: '50%',
  background: color.brandSoft,
  color: color.brandDark,
  fontSize: 11,
  fontWeight: 800,
} as const;

/** Up to two initials. */
function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('');
}
