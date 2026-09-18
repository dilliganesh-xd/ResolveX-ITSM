import { color, font, radius } from '@/lib/tokens';

/**
 * The chart set. Plain SVG, no library.
 *
 * <p>Three rules hold across all of them, and they are the reason these live
 * together rather than being drawn per page:
 *
 * <ul>
 *   <li><b>Every value is labelled.</b> None of these charts asks the reader to
 *       measure a bar against an axis, and none of them leaves meaning resting
 *       on fill colour alone.
 *   <li><b>Data ends are rounded, baselines are square.</b> A 4px radius on the
 *       growing end only, so a bar reads as a quantity rather than a lozenge.
 *   <li><b>Segments are separated by surface, not by a stroke.</b> A 2px gap
 *       between stacked pieces keeps adjacent fills apart without adding a line
 *       that competes with the data.
 * </ul>
 */

export interface Slice {
  label: string;
  /** Omitted when the entry is only naming a series in a legend. */
  value?: number;
  /** Fill. Comes from the caller so tone stays owned by the token module. */
  fill: string;
}

// ------------------------------------------------------------- bar rows

/**
 * One measure across named categories: a labelled row per category, bar length
 * proportional to the largest value.
 *
 * <p>A single series, so it is one hue and carries no legend — the row label
 * names each bar, which is the direct labelling the colour checks ask for.
 */
export function BarRows({ data, max }: { data: { label: string; value: number }[]; max?: number }) {
  const largest = Math.max(1, max ?? 0, ...data.map((d) => d.value));

  if (data.length === 0) {
    return <Empty />;
  }

  return (
    <div style={{ display: 'grid', gap: 11 }}>
      {data.map((row) => {
        const pct = (row.value / largest) * 100;
        return (
          <div key={row.label}>
            <div style={rowHeadStyle}>
              <span style={{ fontWeight: 600, color: color.body, fontSize: 12 }}>{row.label}</span>
              <span style={figureStyle}>{row.value}</span>
            </div>
            <div style={trackStyle}>
              <div
                style={{
                  height: '100%',
                  width: `${pct}%`,
                  background: color.brand,
                  // Rounded at the growing end only; the baseline stays square.
                  borderRadius: '0 4px 4px 0',
                  transition: 'width 250ms ease-out',
                }}
              />
            </div>
          </div>
        );
      })}
    </div>
  );
}

// --------------------------------------------------- stacked proportion

/**
 * A single bar showing how a whole divides — the mix, not the totals.
 *
 * <p>Used where the question is "what is this made of", which a row of
 * separate bars answers badly. Segments below 4% still render at 4% so a small
 * category is visible; the legend carries the true figure, so nothing is
 * misread from the geometry alone.
 */
export function ProportionBar({ data }: { data: Slice[] }) {
  const total = data.reduce((sum, slice) => sum + (slice.value ?? 0), 0);
  const present = data.filter((slice) => (slice.value ?? 0) > 0);

  if (total === 0) {
    return <Empty message="Nothing recorded yet" />;
  }

  return (
    <div>
      <div style={{ display: 'flex', gap: 2, height: 30, marginBottom: 14 }}>
        {present.map((slice, index) => (
          <div
            key={slice.label}
            title={`${slice.label}: ${slice.value}`}
            style={{
              flex: `${Math.max((slice.value ?? 0) / total, 0.04)} 1 0`,
              background: slice.fill,
              borderRadius:
                present.length === 1
                  ? 4
                  : index === 0
                    ? '4px 0 0 4px'
                    : index === present.length - 1
                      ? '0 4px 4px 0'
                      : 0,
            }}
          />
        ))}
      </div>
      <Legend data={data} total={total} />
    </div>
  );
}

// ------------------------------------------------------- stacked rows

export interface StackedRow {
  label: string;
  parts: Slice[];
}

/**
 * Several measures per category, stacked: the composition of each row and the
 * totals between rows, read from one picture.
 */
export function StackedBarRows({ rows, keys }: { rows: StackedRow[]; keys: Slice[] }) {
  const totals = rows.map((row) => row.parts.reduce((sum, part) => sum + (part.value ?? 0), 0));
  const largest = Math.max(1, ...totals);

  if (rows.length === 0) {
    return <Empty />;
  }

  return (
    <div>
      <div style={{ display: 'grid', gap: 11, marginBottom: 14 }}>
        {rows.map((row, rowIndex) => {
          const total = totals[rowIndex] ?? 0;
          return (
            <div key={row.label}>
              <div style={rowHeadStyle}>
                <span style={{ fontWeight: 600, color: color.body, fontSize: 12 }}>{row.label}</span>
                <span style={figureStyle}>{total}</span>
              </div>
              <div style={trackStyle}>
                {total === 0 ? null : (
                  <div style={{ display: 'flex', gap: 2, height: '100%', width: `${(total / largest) * 100}%` }}>
                    {row.parts
                      .filter((part) => (part.value ?? 0) > 0)
                      .map((part, index, visible) => (
                        <div
                          key={part.label}
                          title={`${row.label} · ${part.label}: ${part.value}`}
                          style={{
                            flex: `${part.value ?? 0} 1 0`,
                            background: part.fill,
                            borderRadius: index === visible.length - 1 ? '0 4px 4px 0' : 0,
                          }}
                        />
                      ))}
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>
      <Legend data={keys} />
    </div>
  );
}

// ------------------------------------------------------------- pieces

/**
 * Identity never rests on the swatch alone: each entry names its series, and
 * carries the figure where there is one.
 */
function Legend({ data, total }: { data: Slice[]; total?: number }) {
  return (
    <ul style={legendStyle}>
      {data.map((slice) => (
        <li key={slice.label} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <span aria-hidden style={{ ...swatchStyle, background: slice.fill }} />
          <span style={{ fontSize: 11.5, fontWeight: 600, color: color.body }}>{slice.label}</span>
          {/* A series key carries no figure of its own — printing "0" beside
              "Open" would read as a count rather than a colour key. */}
          {slice.value !== undefined && (
            <span style={{ ...figureStyle, fontSize: 11.5 }}>
              {slice.value}
              {total ? ` · ${Math.round((slice.value / total) * 100)}%` : ''}
            </span>
          )}
        </li>
      ))}
    </ul>
  );
}

function Empty({ message = 'No data yet' }: { message?: string }) {
  return <div style={{ fontSize: 12, fontWeight: 600, color: color.faint }}>{message}</div>;
}

const rowHeadStyle = {
  display: 'flex',
  justifyContent: 'space-between',
  gap: 12,
  marginBottom: 4,
} as const;

const figureStyle = {
  fontFamily: font.mono,
  fontSize: 12,
  color: color.heading,
  fontVariantNumeric: 'tabular-nums',
} as const;

const trackStyle = {
  height: 8,
  borderRadius: 4,
  background: color.hairline,
  overflow: 'hidden',
} as const;

const legendStyle = {
  display: 'flex',
  flexWrap: 'wrap',
  gap: '6px 16px',
  margin: 0,
  padding: 0,
  listStyle: 'none',
} as const;

const swatchStyle = {
  width: 10,
  height: 10,
  borderRadius: radius.small - 4,
  flexShrink: 0,
} as const;
