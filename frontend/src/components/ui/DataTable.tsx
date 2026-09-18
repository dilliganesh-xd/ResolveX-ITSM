import { color, font, size } from '@/lib/tokens';

interface DataTableProps<T> {
  head: string[];
  rows: T[];
  render: (row: T) => React.ReactNode[];
  rowKey: (row: T) => string;
  minWidth?: number;
  /** Tints a row, used to mark an inactive record. */
  rowTint?: (row: T) => string | undefined;
}

/** The shared admin table, so every admin screen has the same rhythm. */
export function DataTable<T>({ head, rows, render, rowKey, minWidth = 720, rowTint }: DataTableProps<T>) {
  // position: relative so absolutely-positioned descendants (the sr-only
  // header label) resolve against this box and are clipped by its scroll,
  // instead of against the page and widening it.
  return (
    <div style={{ overflowX: 'auto', position: 'relative' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', minWidth }}>
        <thead>
          <tr style={{ background: color.thead }}>
            {head.map((heading) => (
              <th
                key={heading}
                style={{
                  fontFamily: font.mono,
                  fontSize: 10.5,
                  fontWeight: 600,
                  color: color.tableHead,
                  letterSpacing: '0.4px',
                  textTransform: 'uppercase',
                  padding: size.cellPadding,
                  textAlign: 'left',
                  whiteSpace: 'nowrap',
                }}
              >
                {heading}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr className="data-row" key={rowKey(row)} style={{ background: rowTint?.(row) }}>
              {render(row).map((cell, index) => (
                <td
                  key={index}
                  style={{
                    padding: size.cellPadding,
                    fontSize: 12.5,
                    color: color.body,
                    borderTop: `1px solid ${color.hairline}`,
                    verticalAlign: 'middle',
                  }}
                >
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
