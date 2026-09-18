import { Button } from './Button';
import { color, font } from '@/lib/tokens';

interface PaginationProps {
  page: number;
  totalPages: number;
  totalElements: number;
  pageSize: number;
  /** How many rows this page actually holds, for the "showing" line. */
  shown: number;
  onChange: (page: number) => void;
  label?: string;
}

/**
 * Page controls with a plain-language position line.
 *
 * <p>Page numbers are windowed around the current page, so a hundred pages do
 * not produce a hundred buttons. Previous and Next are disabled at the ends
 * rather than hidden, so the control does not change shape as you move.
 */
export function Pagination({
  page,
  totalPages,
  totalElements,
  pageSize,
  shown,
  onChange,
  label = 'incidents',
}: PaginationProps) {
  if (totalElements === 0) {
    return null;
  }

  const first = page * pageSize + 1;
  const last = page * pageSize + shown;
  const windowed = pageNumbers(page, totalPages);

  return (
    <nav
      aria-label="Pagination"
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 12,
        flexWrap: 'wrap',
        marginTop: 12,
      }}
    >
      <span style={{ fontSize: 12, fontWeight: 600, color: color.subtle }}>
        Showing {first}–{last} of {totalElements} {label}
      </span>

      {totalPages > 1 && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
          <Button
            variant="secondary"
            style={compact}
            disabled={page === 0}
            onClick={() => onChange(page - 1)}
          >
            Previous
          </Button>

          {windowed.map((candidate, index) =>
            candidate === null ? (
              <span
                key={`gap-${index}`}
                aria-hidden
                style={{ fontFamily: font.mono, fontSize: 12, color: color.faint, padding: '0 2px' }}
              >
                …
              </span>
            ) : (
              <Button
                key={candidate}
                variant={candidate === page ? 'primary' : 'secondary'}
                style={{ ...compact, minWidth: 32 }}
                aria-current={candidate === page ? 'page' : undefined}
                aria-label={`Page ${candidate + 1}`}
                onClick={() => onChange(candidate)}
              >
                {candidate + 1}
              </Button>
            ),
          )}

          <Button
            variant="secondary"
            style={compact}
            disabled={page + 1 >= totalPages}
            onClick={() => onChange(page + 1)}
          >
            Next
          </Button>
        </div>
      )}
    </nav>
  );
}

/** First, last, and a window around the current page; null marks an ellipsis. */
function pageNumbers(page: number, totalPages: number): (number | null)[] {
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, index) => index);
  }
  const pages = new Set<number>([0, totalPages - 1, page]);
  if (page > 0) {
    pages.add(page - 1);
  }
  if (page < totalPages - 1) {
    pages.add(page + 1);
  }

  const sorted = [...pages].sort((a, b) => a - b);
  const result: (number | null)[] = [];
  sorted.forEach((value, index) => {
    if (index > 0 && value - (sorted[index - 1] ?? 0) > 1) {
      result.push(null);
    }
    result.push(value);
  });
  return result;
}

const compact = { height: 30, fontSize: 12 } as const;
