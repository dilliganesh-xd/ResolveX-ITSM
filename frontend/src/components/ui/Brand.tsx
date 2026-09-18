/**
 * The product's name and logo, in one place.
 *
 * <p>Every screen that shows the brand takes it from here. A product gets
 * renamed exactly once if its name lives in one module, and three times a year
 * if it is typed into each page.
 */

export const PRODUCT_NAME = 'ResolveX';

/** What the product is, for the places that need more than the name. */
export const PRODUCT_DESCRIPTOR = 'Service Desk';

/** Title-bar and document-title form. */
export const PRODUCT_TITLE = `${PRODUCT_NAME} · IT ${PRODUCT_DESCRIPTOR}`;

/** Intrinsic size of the supplied artwork, used to reserve space before it loads. */
const LOGO_ASPECT = 2172 / 724;

/**
 * The supplied logo — the one brand asset, used everywhere the product names
 * itself: the sign-in panel and the application rail.
 *
 * <p>The artwork is white on transparent, so it is legible on the brand panel
 * and the navy rail as drawn. On a light surface it is inverted to near-black
 * with a filter rather than shipping a second file: one asset, one place to
 * replace it.
 *
 * <p>Height is derived from the width, so the box is reserved before the image
 * arrives and the layout cannot jump while it loads.
 */
export function BrandLogo({
  width = 240,
  variant = 'light',
}: {
  width?: number;
  /** 'light' = white artwork for dark surfaces. 'dark' = inverted, for light ones. */
  variant?: 'light' | 'dark';
}) {
  return (
    <img
      src="/brand/resolvex.png"
      alt={PRODUCT_NAME}
      width={width}
      height={Math.round(width / LOGO_ASPECT)}
      style={{
        display: 'block',
        width,
        height: 'auto',
        aspectRatio: `${LOGO_ASPECT}`,
        // brightness(0) drives every pixel to black while keeping the alpha
        // channel, which is what an inverted white logo should look like.
        filter: variant === 'dark' ? 'brightness(0) opacity(0.88)' : 'none',
      }}
    />
  );
}
