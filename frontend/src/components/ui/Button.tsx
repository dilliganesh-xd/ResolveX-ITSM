import type { ButtonHTMLAttributes } from 'react';
import { color, font, radius, shadow, size } from '@/lib/tokens';

type Variant = 'primary' | 'secondary';

/** Only primary is filled, per the design language. */
export function Button({ variant = 'primary', style, ...rest }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant }) {
  const base = {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 7,
    height: size.control,
    padding: '0 15px',
    borderRadius: radius.control,
    fontFamily: font.sans,
    fontSize: 13,
    fontWeight: 700,
    cursor: rest.disabled ? 'not-allowed' : 'pointer',
    opacity: rest.disabled ? 0.5 : 1,
    whiteSpace: 'nowrap' as const,
  };

  const variantStyle =
    variant === 'primary'
      ? { background: color.brand, color: '#FFFFFF', border: 'none', boxShadow: shadow.primary }
      : { background: color.surface, color: color.subtle, border: `1px solid ${color.borderInput}` };

  return <button {...rest} style={{ ...base, ...variantStyle, ...style }} />;
}
