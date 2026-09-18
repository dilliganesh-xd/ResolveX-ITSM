import type { Priority } from '@/lib/types';

/** How many of the four bars are lit, by severity. */
const LIT: Record<Priority, number> = {
  LOW: 1,
  MEDIUM: 2,
  HIGH: 3,
  CRITICAL: 4,
};

const BARS = [
  { x: 0, height: 3.5 },
  { x: 3.4, height: 5.5 },
  { x: 6.8, height: 7.5 },
  { x: 10.2, height: 9.5 },
];

/**
 * A four-bar level meter for priority.
 *
 * <p>Replaces the block characters (▁▃▅▇) this used to draw with. Manrope has
 * no glyphs for them, so the browser fell back to whatever it could find and
 * most machines rendered an empty box — the one thing worse than no indicator
 * is a broken one.
 *
 * <p>It exists so severity is not carried by colour alone: the bars rise with
 * the level, which survives greyscale, a colourblind reader and a printout.
 * It takes its colour from `currentColor`, so the pill around it stays the
 * single source of the tone.
 */
export function PriorityLevel({ priority, size = 12 }: { priority: Priority; size?: number }) {
  const lit = LIT[priority];
  return (
    <svg
      width={size}
      height={size * (10 / 12.7)}
      viewBox="0 0 12.7 10"
      fill="none"
      aria-hidden
      focusable="false"
      style={{ flexShrink: 0, display: 'block' }}
    >
      {BARS.map((bar, index) => (
        <rect
          key={bar.x}
          x={bar.x}
          y={10 - bar.height}
          width={2.5}
          height={bar.height}
          rx={1}
          fill="currentColor"
          // Unlit bars stay visible as a track, so the meter reads as "3 of 4"
          // rather than as three floating marks.
          opacity={index < lit ? 1 : 0.25}
        />
      ))}
    </svg>
  );
}
