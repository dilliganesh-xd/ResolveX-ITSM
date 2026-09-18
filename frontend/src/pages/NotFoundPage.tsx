import { Link } from 'react-router-dom';
import { color, radius, size } from '@/lib/tokens';

/** An empty state is a message and a way forward, never just "not found". */
export function NotFoundPage() {
  return (
    <main
      style={{
        padding: size.pagePadding,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        textAlign: 'center',
        marginTop: 48,
      }}
    >
      <div
        style={{
          width: 52,
          height: 52,
          borderRadius: radius.card,
          background: '#F6F8FB',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          marginBottom: 14,
        }}
      >
        <svg width="23" height="23" viewBox="0 0 24 24" fill="none" stroke={color.faint}
             strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
          <path d="M9 13h6M9 17h6M9 9h1M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z" />
        </svg>
      </div>
      <div style={{ fontSize: 14.5, fontWeight: 800, color: color.body }}>Page not found</div>
      <p style={{ fontSize: 12.5, color: color.subtle, fontWeight: 500, margin: '5px 0 16px', maxWidth: 340 }}>
        The page you asked for does not exist in this build.
      </p>
      <Link
        to="/"
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          height: size.control,
          padding: '0 15px',
          borderRadius: radius.control,
          background: color.surface,
          border: `1px solid ${color.borderInput}`,
          color: color.subtle,
          fontSize: 13,
          fontWeight: 700,
          textDecoration: 'none',
        }}
      >
        Back to status
      </Link>
    </main>
  );
}
