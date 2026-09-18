import { color, radius } from '@/lib/tokens';

/**
 * A placeholder shaped like the content that is coming.
 *
 * <p>The shimmer stops entirely under prefers-reduced-motion, and the rows match
 * the real row rhythm so nothing jumps when data lands.
 */
export function Skeleton({ width = '100%', height = 11 }: { width?: string | number; height?: number }) {
  return (
    <span
      aria-hidden
      className="skeleton"
      style={{
        display: 'block',
        width,
        height,
        borderRadius: radius.small,
        background: '#EEF1F5',
      }}
    />
  );
}

/** A stand-in for a table, sized to the columns it is replacing. */
export function TableSkeleton({ rows = 5, columns = 5 }: { rows?: number; columns?: number }) {
  return (
    <div role="status" aria-label="Loading" style={{ display: 'grid', gap: 12 }}>
      {Array.from({ length: rows }).map((_, rowIndex) => (
        <div
          key={rowIndex}
          style={{
            display: 'grid',
            gridTemplateColumns: `repeat(${columns}, 1fr)`,
            gap: 12,
            alignItems: 'center',
            paddingBottom: 12,
            borderBottom: `1px solid ${color.hairline}`,
          }}
        >
          {Array.from({ length: columns }).map((__, cellIndex) => (
            <Skeleton key={cellIndex} width={cellIndex === 1 ? '90%' : '60%'} />
          ))}
        </div>
      ))}
    </div>
  );
}

/** A stand-in for a row of stat cards. */
export function StatsSkeleton({ count = 4 }: { count?: number }) {
  return (
    <div
      role="status"
      aria-label="Loading"
      style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(148px, 1fr))', gap: 12 }}
    >
      {Array.from({ length: count }).map((_, index) => (
        <div
          key={index}
          style={{
            background: color.surface,
            border: `1px solid ${color.border}`,
            borderRadius: radius.card,
            padding: '14px 16px',
            display: 'grid',
            gap: 10,
          }}
        >
          <Skeleton width="60%" height={10} />
          <Skeleton width="40%" height={20} />
        </div>
      ))}
    </div>
  );
}
