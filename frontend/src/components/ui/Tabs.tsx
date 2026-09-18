import type { ComponentType, ReactNode } from 'react';
import type { IconProps } from '@/components/ui/icons';
import { color, font, radius } from '@/lib/tokens';

export interface TabItem<T extends string> {
  id: T;
  label: string;
  icon?: ComponentType<IconProps>;
  /** A count beside the label, for tabs that hold a queue. */
  count?: number;
}

interface TabsProps<T extends string> {
  items: TabItem<T>[];
  active: T;
  onChange: (id: T) => void;
  /** Names the set for assistive technology, e.g. "Incident views". */
  label: string;
}

/**
 * Underline tabs for switching views within a page.
 *
 * <p>Real buttons in a `tablist`, not links: these swap what a panel shows
 * rather than navigating, and arrow-key movement between them is what a screen
 * reader user expects from a tab set.
 *
 * <p>The active tab is marked by weight and colour as well as the underline,
 * so it survives being printed, being viewed in greyscale, or being looked at
 * by someone who cannot separate the brand blue from the grey.
 */
export function Tabs<T extends string>({ items, active, onChange, label }: TabsProps<T>) {
  function onKeyDown(event: React.KeyboardEvent, index: number) {
    const delta = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0;
    if (delta === 0) {
      return;
    }
    event.preventDefault();
    const next = items[(index + delta + items.length) % items.length];
    if (next) {
      onChange(next.id);
    }
  }

  return (
    <div
      role="tablist"
      aria-label={label}
      className="tabs-row"
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 4,
        overflowX: 'auto',
        borderBottom: `1px solid ${color.border}`,
        marginBottom: 16,
      }}
    >
      {items.map((item, index) => {
        const selected = item.id === active;
        return (
          <button
            key={item.id}
            role="tab"
            type="button"
            aria-selected={selected}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(item.id)}
            onKeyDown={(event) => onKeyDown(event, index)}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 7,
              height: 38,
              padding: '0 12px',
              border: 'none',
              borderBottom: `2px solid ${selected ? color.brand : 'transparent'}`,
              background: 'transparent',
              fontFamily: font.sans,
              fontSize: 12.5,
              fontWeight: selected ? 700 : 600,
              color: selected ? color.brand : color.subtle,
              cursor: 'pointer',
              whiteSpace: 'nowrap',
              marginBottom: -1,
            }}
          >
            {item.icon && (
              <span aria-hidden style={{ display: 'flex' }}>
                <item.icon size={15} />
              </span>
            )}
            {item.label}
            {item.count !== undefined && <Count value={item.count} selected={selected} />}
          </button>
        );
      })}
    </div>
  );
}

function Count({ value, selected }: { value: number; selected: boolean }): ReactNode {
  return (
    <span
      style={{
        minWidth: 18,
        padding: '1px 5px',
        borderRadius: radius.pill,
        background: selected ? color.brandSoft : color.hairline,
        color: selected ? color.brandDark : color.subtle,
        fontFamily: font.mono,
        fontSize: 10.5,
        fontWeight: 600,
        textAlign: 'center',
        fontVariantNumeric: 'tabular-nums',
      }}
    >
      {value}
    </span>
  );
}
