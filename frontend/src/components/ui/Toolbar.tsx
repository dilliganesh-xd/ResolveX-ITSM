import type { ComponentType, ReactNode } from 'react';
import type { IconProps } from '@/components/ui/icons';
import { color, font, radius, size } from '@/lib/tokens';

/**
 * The page furniture that sits between a heading and a table: a segmented
 * control for scope, a toolbar for filters, and a section label for grouping.
 *
 * <p>They live together because they are one visual system — the same height,
 * the same radius, the same border — and splitting them across files is how a
 * filter row ends up 2px taller than the control beside it.
 */

// ------------------------------------------------------- segmented control

interface SegmentedProps<T extends string> {
  options: { id: T; label: string }[];
  value: T;
  onChange: (id: T) => void;
  /** Names the group for assistive technology, e.g. "Time range". */
  label: string;
}

/**
 * A small set of mutually exclusive choices, shown all at once.
 *
 * <p>Use this instead of a dropdown when there are four options or fewer and
 * the choice changes what the whole page is showing: a dropdown hides the
 * alternatives behind a click, which is the wrong trade when the alternatives
 * are the point.
 */
export function SegmentedControl<T extends string>({
  options,
  value,
  onChange,
  label,
}: SegmentedProps<T>) {
  return (
    <div
      role="group"
      aria-label={label}
      style={{
        display: 'inline-flex',
        padding: 3,
        gap: 2,
        borderRadius: radius.control,
        background: color.hairline,
        border: `1px solid ${color.border}`,
      }}
    >
      {options.map((option) => {
        const selected = option.id === value;
        return (
          <button
            key={option.id}
            type="button"
            aria-pressed={selected}
            onClick={() => onChange(option.id)}
            style={{
              height: 28,
              padding: '0 12px',
              borderRadius: radius.small,
              border: 'none',
              background: selected ? color.surface : 'transparent',
              boxShadow: selected ? '0 1px 2px rgba(16,24,40,.08)' : 'none',
              fontFamily: font.sans,
              fontSize: 12,
              fontWeight: selected ? 700 : 600,
              color: selected ? color.heading : color.subtle,
              cursor: 'pointer',
              whiteSpace: 'nowrap',
            }}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}

// ---------------------------------------------------------------- toolbar

/**
 * A filter row: controls on the left, actions pushed to the right.
 *
 * <p>It wraps rather than scrolls, so nothing is hidden off the edge of a
 * narrow screen.
 */
export function Toolbar({ children, actions }: { children: ReactNode; actions?: ReactNode }) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        flexWrap: 'wrap',
        marginBottom: 14,
      }}
    >
      {children}
      {actions && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginLeft: 'auto', flexWrap: 'wrap' }}>
          {actions}
        </div>
      )}
    </div>
  );
}

/**
 * A toolbar action: outlined, icon beside label, sized to match the filters it
 * sits next to. `tone` marks the one that does something irreversible.
 */
export function ToolbarButton({
  children,
  icon: Icon,
  onClick,
  tone: buttonTone = 'neutral',
  disabled,
}: {
  children: ReactNode;
  icon?: ComponentType<IconProps>;
  onClick?: () => void;
  tone?: 'neutral' | 'brand' | 'danger';
  disabled?: boolean;
}) {
  const fg =
    buttonTone === 'brand' ? color.brandDark : buttonTone === 'danger' ? '#BE123C' : color.subtle;
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 7,
        height: size.control,
        padding: '0 12px',
        borderRadius: radius.control,
        border: `1px solid ${color.borderInput}`,
        background: color.surface,
        fontFamily: font.sans,
        fontSize: 12.5,
        fontWeight: 700,
        color: fg,
        cursor: disabled ? 'not-allowed' : 'pointer',
        opacity: disabled ? 0.5 : 1,
        whiteSpace: 'nowrap',
      }}
    >
      {Icon && (
        <span aria-hidden style={{ display: 'flex' }}>
          <Icon size={15} />
        </span>
      )}
      {children}
    </button>
  );
}

// ---------------------------------------------------------- section label

/**
 * A heading for a band of content, marked with a brand bar so the eye finds
 * the seams in a long page without needing a rule across it.
 */
export function SectionLabel({ children, hint }: { children: ReactNode; hint?: string }) {
  return (
    <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, margin: '22px 0 12px' }}>
      <span
        aria-hidden
        style={{ width: 3, alignSelf: 'stretch', borderRadius: 2, background: color.brand }}
      />
      <h2
        style={{
          margin: 0,
          fontFamily: font.mono,
          fontSize: 11,
          fontWeight: 600,
          letterSpacing: '0.1em',
          textTransform: 'uppercase',
          color: color.heading,
        }}
      >
        {children}
      </h2>
      {hint && <span style={{ fontSize: 11.5, fontWeight: 500, color: color.faint }}>{hint}</span>}
    </div>
  );
}
