import { useEffect, useRef, useState, type CSSProperties, type FormEvent, type KeyboardEvent } from 'react';
import { useLocation, useNavigate, Navigate } from 'react-router-dom';
import axios from 'axios';
import { useAuth } from '@/auth/useAuth';
import { homeRouteForRole } from '@/auth/context';
import { BrandLogo, PRODUCT_NAME } from '@/components/ui/Brand';
import { IconAlert } from '@/components/ui/icons';
import { color, font, radius, tone } from '@/lib/tokens';
import type { ApiError } from '@/lib/types';

/**
 * The sign-in screen: a brand panel beside the form.
 *
 * <p>Controls are taller here than the 38px the dense application UI uses.
 * This is a single-task page with four controls on it, so the scale that keeps
 * a crowded table readable would only make it look timid.
 *
 * <p>The brand panel is decoration with words on it, so its headline is a
 * block rather than a heading: "Sign in" stays the one h1, including at the
 * widths where the panel is hidden entirely.
 */

const CONTROL_HEIGHT = 46;
const BUTTON_HEIGHT = 48;

export function LoginPage() {
  const { user, login, initialising } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [focused, setFocused] = useState<'email' | 'password' | null>(null);
  const [showResetHint, setShowResetHint] = useState(false);
  const [capsLock, setCapsLock] = useState(false);

  const emailRef = useRef<HTMLInputElement | null>(null);
  const passwordRef = useRef<HTMLInputElement | null>(null);

  // The only thing to do on this page is type an email, so the cursor starts
  // there rather than making everyone click first.
  useEffect(() => {
    emailRef.current?.focus();
  }, []);

  /**
   * Caps Lock is the commonest cause of a password that the user is certain is
   * correct. The state can only be read from a keyboard event, so it is sampled
   * on each keystroke in the field rather than watched globally.
   */
  function sampleCapsLock(event: KeyboardEvent<HTMLInputElement>) {
    setCapsLock(event.getModifierState('CapsLock'));
  }

  if (!initialising && user) {
    return <Navigate to={homeRouteForRole(user.role)} replace />;
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const signedIn = await login(email.trim(), password, remember);
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from ?? homeRouteForRole(signedIn.role), { replace: true });
    } catch (caught) {
      if (axios.isAxiosError<ApiError>(caught)) {
        setError(caught.response?.data?.message ?? 'Could not reach the server. Is the backend running?');
      } else {
        setError('Something went wrong. Please try again.');
      }
      // Send the cursor to the field most likely at fault, with the old value
      // selected so retyping replaces it.
      passwordRef.current?.focus();
      passwordRef.current?.select();
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="auth-split" style={splitStyle}>
      <BrandPanel />

      <section style={formPaneStyle}>
        <div style={{ width: '100%', maxWidth: 400 }}>
          {/* Shown only where the brand panel is not: a phone still needs to
              know whose service desk this is. */}
          <div className="auth-compact-brand" style={compactBrandStyle}>
            <BrandLogo variant="dark" width={180} />
          </div>

          <h1 style={{ margin: 0, fontSize: 27, fontWeight: 800, letterSpacing: '-0.7px', color: color.heading }}>
            Sign in
          </h1>
          <p style={{ margin: '6px 0 26px', fontSize: 13.5, fontWeight: 500, color: color.muted }}>
            Enter your credentials to access your dashboard.
          </p>

          <form onSubmit={onSubmit}>
            <Field label="Email Address" htmlFor="email">
              <div style={{ position: 'relative' }}>
                <span style={adornmentStyle('left')} aria-hidden>
                  <MailIcon />
                </span>
                <input
                  id="email"
                  ref={emailRef}
                  type="email"
                  required
                  autoComplete="username"
                  placeholder="you@company.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  onFocus={() => setFocused('email')}
                  onBlur={() => setFocused(null)}
                  style={inputStyle(focused === 'email')}
                />
              </div>
            </Field>

            <Field label="Password" htmlFor="password">
              <div style={{ position: 'relative' }}>
                <span style={adornmentStyle('left')} aria-hidden>
                  <LockIcon />
                </span>
                <input
                  id="password"
                  ref={passwordRef}
                  type={showPassword ? 'text' : 'password'}
                  required
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  onKeyUp={sampleCapsLock}
                  onKeyDown={sampleCapsLock}
                  onFocus={() => setFocused('password')}
                  onBlur={() => {
                    setFocused(null);
                    setCapsLock(false);
                  }}
                  style={{ ...inputStyle(focused === 'password'), paddingRight: 44 }}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((shown) => !shown)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  aria-pressed={showPassword}
                  style={toggleStyle}
                >
                  {showPassword ? <EyeOffIcon /> : <EyeIcon />}
                </button>
              </div>
              {capsLock && (
                <p role="status" style={capsLockStyle}>
                  <IconAlert size={13} />
                  Caps Lock is on
                </p>
              )}
            </Field>

            <div style={rememberRowStyle}>
              <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={remember}
                  onChange={(e) => setRemember(e.target.checked)}
                  style={{ width: 15, height: 15, accentColor: color.brand, cursor: 'pointer', margin: 0 }}
                />
                <span style={{ fontSize: 12.5, fontWeight: 600, color: color.body }}>Remember me</span>
              </label>

              <button
                type="button"
                onClick={() => setShowResetHint((shown) => !shown)}
                aria-expanded={showResetHint}
                style={linkButtonStyle}
              >
                Forgot password?
              </button>
            </div>

            {showResetHint && (
              <p style={hintStyle}>
                There is no self-service reset in V1. An administrator can set a new password for
                you from the Users page.
              </p>
            )}

            {error && (
              <div role="alert" style={errorStyle}>
                <span aria-hidden style={{ fontWeight: 800 }}>
                  !
                </span>
                <span>{error}</span>
              </div>
            )}

            <button type="submit" disabled={submitting} style={submitStyle(submitting)}>
              {submitting ? 'Signing in...' : 'Sign in'}
              {!submitting && <ArrowIcon />}
            </button>
          </form>

          <p style={secureLineStyle}>
            <ShieldIcon />
            {/* The token the API issues is valid for one hour from sign-in. */}
            <span>Secure &middot; Session expires 60 minutes after sign-in</span>
          </p>

          <div style={infoBoxStyle}>
            <div>Accounts are created by an administrator. There is no self sign-up.</div>
            {/* The seeded demo accounts exist only in local development. A
                production build must not advertise an email and password. */}
            {import.meta.env.DEV && (
              <div style={{ marginTop: 6 }}>
                Development accounts &mdash; password{' '}
                <code style={{ fontFamily: font.mono, color: color.subtle }}>Password123!</code>
                <br />
                admin@itsm.local &middot; agent@itsm.local &middot; employee@itsm.local
              </div>
            )}
          </div>
        </div>
      </section>
    </main>
  );
}

// ------------------------------------------------------------ brand panel

function BrandPanel() {
  return (
    <aside className="auth-brand" style={brandPaneStyle}>
      {/* Two soft discs, clipped by the panel, so a flat gradient gains some
          depth without carrying an image. */}
      <span aria-hidden style={discStyle(560, -190, -170)} />
      <span aria-hidden style={discStyle(300, 170, -110)} />

      <div style={{ position: 'relative' }}>
        <BrandLogo variant="light" width={250} />
      </div>

      <div style={{ position: 'relative', marginTop: 'auto', paddingTop: 32 }}>
        <p style={headlineStyle}>
          Every incident,
          <br />
          From report to resolved.
        </p>
        <p style={brandSubtitleStyle}>
          One queue for the whole organisation, two SLA clocks on every ticket, and an audit trail
          that records who changed what, and when.
        </p>

        <div style={cardsGridStyle}>
          <BrandCard icon={<TicketIcon />} title="Incident Desk" sub="Report and triage" />
          <BrandCard icon={<ClockIcon />} title="SLA Tracking" sub="Two clocks per ticket" />
          <BrandCard icon={<LayersIcon />} title="Role-based Access" sub="Employee, agent, admin" />
        </div>
      </div>

      <div style={brandFooterStyle}>
        <span>
          &copy; 2026 {PRODUCT_NAME} &middot; Secured with JWT authentication
        </span>
        <span style={versionPillStyle}>v0.1.0 &middot; MVP</span>
      </div>
    </aside>
  );
}

function BrandCard({ icon, title, sub }: { icon: React.ReactNode; title: string; sub: string }) {
  return (
    <div style={brandCardStyle}>
      <span aria-hidden style={cardIconStyle}>
        {icon}
      </span>
      <div style={{ marginTop: 10, fontSize: 12.5, fontWeight: 700, color: '#FFFFFF' }}>{title}</div>
      <div
        style={{
          marginTop: 2,
          fontSize: 11,
          fontWeight: 500,
          color: 'rgba(255,255,255,.62)',
          lineHeight: 1.45,
        }}
      >
        {sub}
      </div>
    </div>
  );
}

// ------------------------------------------------------------------ parts

function Field({
  label,
  htmlFor,
  children,
}: {
  label: string;
  htmlFor: string;
  children: React.ReactNode;
}) {
  return (
    <div style={{ marginBottom: 16 }}>
      <label
        htmlFor={htmlFor}
        style={{ display: 'block', marginBottom: 7, fontSize: 12.5, fontWeight: 700, color: color.heading }}
      >
        {label}
      </label>
      {children}
    </div>
  );
}

// ------------------------------------------------------------------ icons
// Inline rather than a dependency: eight small glyphs do not justify a library.

const strokeProps = {
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.7,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
};

function MailIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" {...strokeProps}>
      <rect x="2.5" y="4.5" width="19" height="15" rx="2.5" />
      <path d="M3 6.5 12 13 21 6.5" />
    </svg>
  );
}

function LockIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" {...strokeProps}>
      <rect x="4" y="10.5" width="16" height="10" rx="2.5" />
      <path d="M8 10.5V7.5a4 4 0 0 1 8 0v3" />
    </svg>
  );
}

function EyeIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" {...strokeProps}>
      <path d="M2 12s3.8-6.5 10-6.5S22 12 22 12s-3.8 6.5-10 6.5S2 12 2 12Z" />
      <circle cx="12" cy="12" r="2.8" />
    </svg>
  );
}

function EyeOffIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" {...strokeProps}>
      <path d="M9.9 5.7A9.9 9.9 0 0 1 12 5.5c6.2 0 10 6.5 10 6.5a18 18 0 0 1-3.2 4M6.3 7.8A17.6 17.6 0 0 0 2 12s3.8 6.5 10 6.5a9.8 9.8 0 0 0 4-.83" />
      <path d="M3 3l18 18" />
    </svg>
  );
}

function ShieldIcon() {
  return (
    <svg width="12" height="12" viewBox="0 0 24 24" {...strokeProps} style={{ flexShrink: 0 }}>
      <path d="M12 2.8 20 6v6c0 4.8-3.4 7.7-8 9.2-4.6-1.5-8-4.4-8-9.2V6Z" />
    </svg>
  );
}

function ArrowIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" {...strokeProps} aria-hidden>
      <path d="M5 12h13M12.5 6l6 6-6 6" />
    </svg>
  );
}

function TicketIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" {...strokeProps}>
      <rect x="4" y="3" width="16" height="18" rx="2.5" />
      <path d="M8.5 8.5h7M8.5 12.5h7M8.5 16.5h4" />
    </svg>
  );
}

function ClockIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" {...strokeProps}>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 6.8V12l3.6 2.2" />
    </svg>
  );
}

function LayersIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" {...strokeProps}>
      <path d="M12 3 21 8l-9 5-9-5Z" />
      <path d="M3 13.2 12 18l9-4.8" />
    </svg>
  );
}

// ----------------------------------------------------------------- styles

const splitStyle: CSSProperties = {
  minHeight: '100vh',
  display: 'grid',
  gridTemplateColumns: 'minmax(0, 1.05fr) minmax(0, 1fr)',
  background: color.surface,
};

const brandPaneStyle: CSSProperties = {
  position: 'relative',
  overflow: 'hidden',
  display: 'flex',
  flexDirection: 'column',
  padding: '42px 50px 34px',
  background: `linear-gradient(158deg, ${color.brandDeeper} 0%, ${color.brandDeep} 46%, ${color.brand} 100%)`,
  color: '#FFFFFF',
};

function discStyle(diameter: number, top: number, right: number): CSSProperties {
  return {
    position: 'absolute',
    top,
    right,
    width: diameter,
    height: diameter,
    borderRadius: '50%',
    background: 'rgba(255,255,255,.055)',
    pointerEvents: 'none',
  };
}

const headlineStyle: CSSProperties = {
  margin: 0,
  fontSize: 38,
  fontWeight: 800,
  lineHeight: 1.16,
  letterSpacing: '-1.1px',
  color: '#FFFFFF',
};

const brandSubtitleStyle: CSSProperties = {
  margin: '18px 0 0',
  maxWidth: 430,
  fontSize: 13.5,
  fontWeight: 500,
  lineHeight: 1.65,
  color: 'rgba(255,255,255,.72)',
};

const cardsGridStyle: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(3, minmax(0, 1fr))',
  gap: 12,
  maxWidth: 500,
  marginTop: 32,
};

const brandCardStyle: CSSProperties = {
  padding: 14,
  borderRadius: radius.card,
  background: 'rgba(255,255,255,.08)',
  border: '1px solid rgba(255,255,255,.14)',
};

const cardIconStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  width: 30,
  height: 30,
  borderRadius: radius.control,
  background: 'rgba(255,255,255,.14)',
  color: '#FFFFFF',
};

const brandFooterStyle: CSSProperties = {
  position: 'relative',
  display: 'flex',
  alignItems: 'center',
  gap: 10,
  flexWrap: 'wrap',
  marginTop: 34,
  fontSize: 11.5,
  fontWeight: 500,
  color: 'rgba(255,255,255,.55)',
};

const versionPillStyle: CSSProperties = {
  padding: '3px 10px',
  borderRadius: radius.pill,
  border: '1px solid rgba(255,255,255,.22)',
  fontFamily: font.mono,
  fontSize: 10.5,
  color: 'rgba(255,255,255,.75)',
};

const formPaneStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  padding: '40px 44px',
  background: color.surface,
};

const compactBrandStyle: CSSProperties = {
  display: 'none',
  marginBottom: 26,
};

function inputStyle(isFocused: boolean): CSSProperties {
  return {
    width: '100%',
    height: CONTROL_HEIGHT,
    padding: '0 14px 0 40px',
    border: `1px solid ${isFocused ? color.brand : color.borderInput}`,
    borderRadius: radius.control,
    boxShadow: isFocused ? `0 0 0 3px ${color.brandSoft}` : 'none',
    outline: 'none',
    fontSize: 13.5,
    fontFamily: font.sans,
    background: color.surface,
    color: color.heading,
  };
}

function adornmentStyle(side: 'left' | 'right'): CSSProperties {
  return {
    position: 'absolute',
    top: 0,
    [side]: 13,
    height: CONTROL_HEIGHT,
    display: 'flex',
    alignItems: 'center',
    color: color.faint,
  };
}

const toggleStyle: CSSProperties = {
  ...adornmentStyle('right'),
  border: 'none',
  background: 'transparent',
  padding: 0,
  cursor: 'pointer',
};

const capsLockStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 6,
  margin: '7px 0 0',
  fontSize: 11.5,
  fontWeight: 700,
  color: tone.amber.fg,
};

const rememberRowStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  gap: 12,
  flexWrap: 'wrap',
  margin: '2px 0 20px',
};

const linkButtonStyle: CSSProperties = {
  border: 'none',
  background: 'transparent',
  padding: 0,
  fontFamily: font.sans,
  fontSize: 12.5,
  fontWeight: 700,
  color: color.brand,
  cursor: 'pointer',
};

const hintStyle: CSSProperties = {
  margin: '0 0 16px',
  padding: '9px 11px',
  borderRadius: radius.control,
  background: tone.blue.bg,
  border: `1px solid ${tone.blue.line}`,
  fontSize: 12,
  fontWeight: 600,
  lineHeight: 1.5,
  color: tone.blue.fg,
};

const errorStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'flex-start',
  gap: 8,
  marginBottom: 16,
  padding: '10px 12px',
  borderRadius: radius.control,
  background: tone.red.bg,
  border: `1px solid ${tone.red.line}`,
  color: tone.red.fg,
  fontSize: 12.5,
  fontWeight: 600,
};

function submitStyle(busy: boolean): CSSProperties {
  return {
    width: '100%',
    height: BUTTON_HEIGHT,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    borderRadius: radius.control,
    border: 'none',
    background: color.brand,
    color: '#FFFFFF',
    fontFamily: font.sans,
    fontSize: 14,
    fontWeight: 700,
    cursor: busy ? 'not-allowed' : 'pointer',
    opacity: busy ? 0.55 : 1,
    boxShadow: '0 2px 8px rgba(37,99,235,.28)',
  };
}

const secureLineStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  gap: 6,
  margin: '16px 0 20px',
  fontSize: 11.5,
  fontWeight: 500,
  color: color.faint,
};

const infoBoxStyle: CSSProperties = {
  padding: '12px 14px',
  borderRadius: radius.control,
  background: color.hairline,
  border: `1px solid ${color.border}`,
  fontSize: 11.5,
  fontWeight: 500,
  lineHeight: 1.65,
  color: color.subtle,
  textAlign: 'center',
};
