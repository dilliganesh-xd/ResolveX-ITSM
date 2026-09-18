/**
 * The single source of design truth for this application.
 *
 * Values are transcribed from the design language reference this project was
 * given. That reference documents a product that drifted by keeping two token
 * sources (tokens.js and a Tailwind theme); this project deliberately has one.
 * Nothing here should be duplicated into a CSS file, a Tailwind config or a
 * literal hex in a component.
 */

export const color = {
  // Brand
  brand: '#2563EB',
  brandDark: '#1D4ED8',
  brandSoft: '#EEF4FF',
  brandSofter: '#EFF6FF',
  // The deep end of the same hue, for full-bleed brand surfaces such as the
  // sign-in panel. Only a surface that carries white text should use these.
  brandDeep: '#1E40AF',
  brandDeeper: '#172B6B',

  // Text scale
  heading: '#0F172A',
  body: '#334155',
  muted: '#475569', // contrast floor, >= 4.5:1 — never lighter for body copy
  subtle: '#64748B',
  faint: '#94A3B8', // metadata only
  tableHead: '#AAB1BA',

  // Surfaces
  pageBg: '#F6F7F9',
  surface: '#FFFFFF',
  border: '#EBEDF0',
  hairline: '#F2F4F6',
  borderInput: '#E2E8F0',
  thead: '#FAFBFC',
  rowHover: '#FAFBFF',
  sidebar: '#0F172A',
} as const;

/**
 * Status tones are triples — fill, text, dot — so a pill, a tile and a marker
 * of the same state always agree. Never hand-pick a colour; take a tone.
 */
export const tone = {
  green: { bg: '#F0FDF4', fg: '#15803D', dot: '#16A34A', line: '#D1E7DD' },
  amber: { bg: '#FFF7ED', fg: '#B45309', dot: '#F59E0B', line: '#FED7AA' },
  red: { bg: '#FEF2F2', fg: '#BE123C', dot: '#EF4444', line: '#FECDD3' },
  blue: { bg: '#EEF4FF', fg: '#1D4ED8', dot: '#60A5FA', line: '#DBEAFE' },
  purple: { bg: '#F3EFFE', fg: '#7C3AED', dot: '#7C3AED', line: '#DDD0FB' },
  grey: { bg: '#F3F4F6', fg: '#6B7480', dot: '#94A3B8', line: '#E5E7EB' },
} as const;

export type ToneName = keyof typeof tone;

/** Series colour comes from position, so a category keeps its colour everywhere. */
export const chartSeries = [
  '#2563EB', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6',
  '#06B6D4', '#F97316', '#84CC16', '#EC4899', '#6B7280',
] as const;

export const font = {
  sans: "'Manrope', system-ui, -apple-system, 'Segoe UI', sans-serif",
  mono: "'JetBrains Mono', ui-monospace, 'SF Mono', Menlo, monospace",
} as const;

/** Radius encodes size: the larger the object, the rounder it is. */
export const radius = {
  card: 14,
  control: 9,
  pill: 20,
  small: 7,
} as const;

export const shadow = {
  card: '0 1px 2px rgba(16,24,40,.04)',
  primary: '0 1px 3px rgba(37,99,235,.3)',
} as const;

export const size = {
  control: 38,
  controlMobile: 44, // WCAG 2.5.5
  sidebar: 254,
  topbar: 56,
  cellPadding: '11px 14px',
  pagePadding: '24px 26px 40px',
  pagePaddingMobile: '16px 16px 32px',
} as const;
