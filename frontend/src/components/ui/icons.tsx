import type { ReactNode } from 'react';

/**
 * The icon family.
 *
 * <p>One stroke width, one corner treatment, one 24-unit grid, drawn here
 * rather than pulled from a dependency: the set is small and a library would
 * ship several hundred glyphs to use a dozen. Every icon goes through
 * {@link Glyph}, so the stroke cannot drift from one icon to the next.
 *
 * <p>Icons take their colour from `currentColor` and are `aria-hidden`: they
 * decorate a label, they never replace one. A control with no visible text
 * needs its own `aria-label` at the call site.
 */

const STROKE = 1.7;

function Glyph({ size = 16, children }: { size?: number; children: ReactNode }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={STROKE}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
      focusable="false"
      style={{ flexShrink: 0, display: 'block' }}
    >
      {children}
    </svg>
  );
}

export interface IconProps {
  size?: number;
}

// ------------------------------------------------------------- navigation

export function IconDashboard({ size }: IconProps) {
  return (
    <Glyph size={size}>
      <rect x="3" y="3" width="7.5" height="7.5" rx="1.8" />
      <rect x="13.5" y="3" width="7.5" height="7.5" rx="1.8" />
      <rect x="3" y="13.5" width="7.5" height="7.5" rx="1.8" />
      <rect x="13.5" y="13.5" width="7.5" height="7.5" rx="1.8" />
    </Glyph>
  );
}

export function IconInbox({ size }: IconProps) {
  return (
    <Glyph size={size}>
      <path d="M3 13h4l1.5 3h7L17 13h4" />
      <path d="M4.6 5.5h14.8l1.6 7.5v4.2a1.8 1.8 0 0 1-1.8 1.8H4.8A1.8 1.8 0 0 1 3 17.2V13Z" />
    </Glyph>
  );
}

export function IconPlus({ size }: IconProps) {
  return (
    <Glyph size={size}>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 8.2v7.6M8.2 12h7.6" />
    </Glyph>
  );
}

export function IconQueue({ size }: IconProps) {
  return (
    <Glyph size={size}>
      <path d="M4 6.5h16M4 12h16M4 17.5h10" />
    </Glyph>
  );
}

export function IconChart({ size }: IconProps) {
  return (
    <Glyph size={size}>
      <path d="M4 20V4" />
      <path d="M4 20h16" />
      <rect x="7.5" y="12" width="3" height="5" rx="1" />
      <rect x="13" y="8" width="3" height="9" rx="1" />
    </Glyph>
  );
}

export function IconUsers({ size }: IconProps) {
  return (
    <Glyph size={size}>
      <circle cx="9" cy="8" r="3.4" />
      <path d="M3.2 19.2a6 6 0 0 1 11.6 0" />
      <path d="M16.5 5.2a3.4 3.4 0 0 1 0 6.4M17.6 13.6a6 6 0 0 1 3.2 4.6" />
    </Glyph>
  );
}

export function IconTeam({ size }: IconProps) {
  return (
    <Glyph size={size}>
      <rect x="3" y="9" width="7" height="11" rx="1.8" />
      <rect x="14" y="4" width="7" height="16" rx="1.8" />
      <path d="M6.5 13h0M6.5 16.5h0M17.5 8h0M17.5 11.5h0M17.5 15h0" />
    </Glyph>
  );
}

export function IconClock({ size }: IconProps) {
  return (
    <Glyph size={size}>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 6.8V12l3.6 2.2" />
    </Glyph>
  );
}

// ------------------------------------------------------------------- misc

export function IconChevronRight({ size }: IconProps) {
  return (
    <Glyph size={size}>
      <path d="M9.5 5.5 16 12l-6.5 6.5" />
    </Glyph>
  );
}

export function IconChevronDown({ size }: IconProps) {
  return (
    <Glyph size={size}>
      <path d="M5.5 9.5 12 16l6.5-6.5" />
    </Glyph>
  );
}

export function IconArrowRight({ size }: IconProps) {
  return (
    <Glyph size={size}>
      <path d="M5 12h13M12.5 6l6 6-6 6" />
    </Glyph>
  );
}

export function IconSignOut({ size }: IconProps) {
  return (
    <Glyph size={size}>
      <path d="M14.5 7.5V5.8A1.8 1.8 0 0 0 12.7 4H5.8A1.8 1.8 0 0 0 4 5.8v12.4A1.8 1.8 0 0 0 5.8 20h6.9a1.8 1.8 0 0 0 1.8-1.8v-1.7" />
      <path d="M9.5 12h11M17 8.5l3.5 3.5-3.5 3.5" />
    </Glyph>
  );
}

export function IconSearch({ size }: IconProps) {
  return (
    <Glyph size={size}>
      <circle cx="11" cy="11" r="6.5" />
      <path d="m16 16 4 4" />
    </Glyph>
  );
}

export function IconDownload({ size }: IconProps) {
  return (
    <Glyph size={size}>
      <path d="M12 4v10M8 10.5l4 4 4-4" />
      <path d="M4.5 18.5h15" />
    </Glyph>
  );
}

export function IconCheck({ size }: IconProps) {
  return (
    <Glyph size={size}>
      <circle cx="12" cy="12" r="9" />
      <path d="m8.2 12.3 2.6 2.6 5-5.4" />
    </Glyph>
  );
}

export function IconAlert({ size }: IconProps) {
  return (
    <Glyph size={size}>
      <path d="M12 4.3 21 19.2H3Z" />
      <path d="M12 10v3.6M12 16.4h0" />
    </Glyph>
  );
}

export function IconShield({ size }: IconProps) {
  return (
    <Glyph size={size}>
      <path d="M12 2.8 20 6v6c0 4.8-3.4 7.7-8 9.2-4.6-1.5-8-4.4-8-9.2V6Z" />
    </Glyph>
  );
}

export function IconMail({ size }: IconProps) {
  return (
    <Glyph size={size}>
      <rect x="2.5" y="4.5" width="19" height="15" rx="2.5" />
      <path d="M3 6.5 12 13l9-6.5" />
    </Glyph>
  );
}

export function IconLock({ size }: IconProps) {
  return (
    <Glyph size={size}>
      <rect x="4" y="10.5" width="16" height="10" rx="2.5" />
      <path d="M8 10.5V7.5a4 4 0 0 1 8 0v3" />
    </Glyph>
  );
}

export function IconEye({ size }: IconProps) {
  return (
    <Glyph size={size}>
      <path d="M2 12s3.8-6.5 10-6.5S22 12 22 12s-3.8 6.5-10 6.5S2 12 2 12Z" />
      <circle cx="12" cy="12" r="2.8" />
    </Glyph>
  );
}

export function IconEyeOff({ size }: IconProps) {
  return (
    <Glyph size={size}>
      <path d="M9.9 5.7A9.9 9.9 0 0 1 12 5.5c6.2 0 10 6.5 10 6.5a18 18 0 0 1-3.2 4M6.3 7.8A17.6 17.6 0 0 0 2 12s3.8 6.5 10 6.5a9.8 9.8 0 0 0 4-.83" />
      <path d="m3 3 18 18" />
    </Glyph>
  );
}

export function IconTicket({ size }: IconProps) {
  return (
    <Glyph size={size}>
      <rect x="4" y="3" width="16" height="18" rx="2.5" />
      <path d="M8.5 8.5h7M8.5 12.5h7M8.5 16.5h4" />
    </Glyph>
  );
}

export function IconLayers({ size }: IconProps) {
  return (
    <Glyph size={size}>
      <path d="M12 3 21 8l-9 5-9-5Z" />
      <path d="m3 13.2 9 4.8 9-4.8" />
    </Glyph>
  );
}

export function IconEdit({ size }: IconProps) {
  return (
    <Glyph size={size}>
      <path d="M4 20h4.2L19 9.2a2.4 2.4 0 0 0-3.4-3.4L4.8 16.6Z" />
      <path d="M14.5 6.8 17.2 9.5" />
    </Glyph>
  );
}

export function IconKey({ size }: IconProps) {
  return (
    <Glyph size={size}>
      <circle cx="8" cy="15.5" r="4" />
      <path d="m11 12.6 8-8M16.4 7.2l2.2 2.2M18.6 5l2.2 2.2" />
    </Glyph>
  );
}

export function IconPower({ size }: IconProps) {
  return (
    <Glyph size={size}>
      <path d="M12 3.5v8" />
      <path d="M17.5 6.6a7.5 7.5 0 1 1-11 0" />
    </Glyph>
  );
}

export function IconMembers({ size }: IconProps) {
  return (
    <Glyph size={size}>
      <circle cx="9" cy="8" r="3.2" />
      <path d="M3.4 19a5.8 5.8 0 0 1 11.2 0" />
      <path d="M17 11.5h4M19 9.5v4" />
    </Glyph>
  );
}
