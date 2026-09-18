import { useEffect, useRef, useState, type ComponentType } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '@/auth/useAuth';
import { humanise } from '@/lib/display';
import { BrandLogo, PRODUCT_NAME } from '@/components/ui/Brand';
import {
  IconChart,
  IconChevronRight,
  IconClock,
  IconDashboard,
  IconInbox,
  IconPlus,
  IconQueue,
  IconSignOut,
  IconTeam,
  IconUsers,
  type IconProps,
} from '@/components/ui/icons';
import { color, font, radius, size } from '@/lib/tokens';
import type { RoleName } from '@/lib/types';
import { NotificationBell } from './NotificationBell';

interface NavItem {
  to: string;
  label: string;
  allow: RoleName[];
  /** Grouping caption, so a long admin rail reads as sections rather than a list. */
  group: string;
  icon: ComponentType<IconProps>;
}

/**
 * Navigation is filtered by role, so a user is never shown a link that the API
 * would reject. The filtering is cosmetic; ProtectedRoute and the API are what
 * actually enforce it.
 *
 * <p>Every item carries an icon as well as a label. An icon alone is a guess;
 * a label alone gives the eye nothing to lock onto when scanning a rail.
 */
const NAV: NavItem[] = [
  { to: '/employee', label: 'My Incidents', allow: ['EMPLOYEE'], group: 'Incidents', icon: IconInbox },
  { to: '/employee/new', label: 'Report Incident', allow: ['EMPLOYEE'], group: 'Incidents', icon: IconPlus },
  { to: '/agent', label: 'Dashboard', allow: ['SUPPORT_AGENT'], group: 'Work', icon: IconDashboard },
  { to: '/agent/queue', label: 'Incident Queue', allow: ['SUPPORT_AGENT'], group: 'Work', icon: IconQueue },
  { to: '/admin', label: 'Dashboard', allow: ['ADMIN'], group: 'Overview', icon: IconDashboard },
  { to: '/admin/incidents', label: 'All Incidents', allow: ['ADMIN'], group: 'Overview', icon: IconQueue },
  { to: '/admin/reports', label: 'Reports', allow: ['ADMIN'], group: 'Overview', icon: IconChart },
  { to: '/admin/users', label: 'Users', allow: ['ADMIN'], group: 'Administration', icon: IconUsers },
  { to: '/admin/teams', label: 'Teams', allow: ['ADMIN'], group: 'Administration', icon: IconTeam },
  {
    to: '/admin/sla-policies',
    label: 'SLA Policies',
    allow: ['ADMIN'],
    group: 'Administration',
    icon: IconClock,
  },
];

/** The title shown in the header, matched longest-path first. */
const TITLES: [string, string][] = [
  ['/employee/new', 'Report Incident'],
  ['/employee', 'My Incidents'],
  ['/agent/queue', 'Incident Queue'],
  ['/agent', 'Dashboard'],
  ['/admin/incidents', 'All Incidents'],
  ['/admin/reports', 'Reports'],
  ['/admin/users', 'Users'],
  ['/admin/teams', 'Teams'],
  ['/admin/sla-policies', 'SLA Policies'],
  ['/admin', 'Dashboard'],
  ['/incidents/', 'Incident'],
];

/** The breadcrumb's first crumb: which part of the product you are standing in. */
const SECTION: Record<RoleName, string> = {
  EMPLOYEE: 'Support',
  SUPPORT_AGENT: 'Operations',
  ADMIN: 'Administration',
};

export function AppShell() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement | null>(null);

  // Escape closes the profile menu, and so does a click outside it.
  useEffect(() => {
    if (!menuOpen) {
      return;
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setMenuOpen(false);
      }
    }
    function onClick(event: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setMenuOpen(false);
      }
    }
    window.addEventListener('keydown', onKey);
    window.addEventListener('mousedown', onClick);
    return () => {
      window.removeEventListener('keydown', onKey);
      window.removeEventListener('mousedown', onClick);
    };
  }, [menuOpen]);

  if (!user) {
    return null;
  }

  const items = NAV.filter((item) => item.allow.includes(user.role));
  const groups = [...new Set(items.map((item) => item.group))];
  const title = TITLES.find(([path]) => location.pathname.startsWith(path))?.[1] ?? PRODUCT_NAME;

  return (
    <div className="app-shell" style={{ display: 'flex', minHeight: '100vh' }}>
      <nav
        className="app-rail"
        aria-label="Main"
        style={{
          width: size.sidebar,
          flexShrink: 0,
          background: color.sidebar,
          padding: '16px 12px',
          display: 'flex',
          flexDirection: 'column',
          gap: 2,
          // Pinned: navigation and the sign-out control stay reachable from the
          // bottom of a long incident list, instead of scrolling away with it.
          position: 'sticky',
          top: 0,
          height: '100vh',
          overflowY: 'auto',
        }}
      >
        <div
          className="app-rail-brand"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            color: '#FFFFFF',
            padding: '0 10px 16px',
          }}
        >
          <BrandLogo variant="light" width={165} />
        </div>

        {groups.map((group) => (
          <div key={group} className="app-rail-group">
            <div
              className="app-rail-caption"
              style={{
                fontFamily: font.mono,
                fontSize: 10,
                fontWeight: 600,
                letterSpacing: '0.1em',
                textTransform: 'uppercase',
                color: 'rgba(255,255,255,.28)',
                padding: '12px 10px 6px',
              }}
            >
              {group}
            </div>
            {items
              .filter((item) => item.group === group)
              .map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.to === '/employee' || item.to === '/admin' || item.to === '/agent'}
                  className="app-rail-link"
                  style={({ isActive }) => ({
                    display: 'flex',
                    alignItems: 'center',
                    gap: 10,
                    height: 38,
                    padding: '0 11px',
                    borderRadius: radius.control,
                    fontSize: 13,
                    fontWeight: isActive ? 700 : 600,
                    textDecoration: 'none',
                    color: isActive ? '#FFFFFF' : 'rgba(255,255,255,.66)',
                    background: isActive ? color.brand : 'transparent',
                    boxShadow: isActive ? '0 1px 6px rgba(37,99,235,.45)' : 'none',
                  })}
                >
                  {({ isActive }) => (
                    <>
                      <span
                        aria-hidden
                        style={{ color: isActive ? '#FFFFFF' : 'rgba(255,255,255,.5)', display: 'flex' }}
                      >
                        <item.icon size={17} />
                      </span>
                      <span>{item.label}</span>
                    </>
                  )}
                </NavLink>
              ))}
          </div>
        ))}

        {/* Identity sits at the foot of the rail, where a desk's nameplate goes.
            Hidden on narrow screens, where the rail becomes a strip and the
            header's account menu takes over. */}
        <div className="app-rail-user" style={railUserStyle}>
          <span aria-hidden style={railAvatarStyle}>
            {initials(user.name)}
          </span>
          <span style={{ minWidth: 0, flex: 1 }}>
            <span style={railNameStyle}>{user.name}</span>
            <span style={railRoleStyle}>{humanise(user.role)}</span>
          </span>
          <button
            type="button"
            onClick={logout}
            aria-label="Sign out"
            title="Sign out"
            className="app-rail-signout"
            style={railSignOutStyle}
          >
            <IconSignOut size={16} />
          </button>
        </div>
      </nav>

      <div
        className="app-main-column"
        style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}
      >
        {/* One header across every screen: where you are on the left, what is
            waiting for you and who you are on the right. */}
        <header
          className="app-header"
          style={{
            minHeight: size.topbar,
            flexShrink: 0,
            background: color.surface,
            borderBottom: `1px solid ${color.border}`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 12,
            padding: '0 20px',
            position: 'sticky',
            top: 0,
            zIndex: 120,
          }}
        >
          {/* Section, then page. Two crumbs is enough for a product this flat —
              it orients without pretending to a hierarchy that is not there. */}
          <nav aria-label="Breadcrumb" style={{ minWidth: 0 }}>
            <ol style={crumbListStyle}>
              <li>
                <span style={sectionPillStyle}>{SECTION[user.role]}</span>
              </li>
              <li aria-hidden style={{ display: 'flex', color: color.faint }}>
                <IconChevronRight size={13} />
              </li>
              <li style={{ minWidth: 0 }}>
                <h1 style={crumbTitleStyle}>{title}</h1>
              </li>
            </ol>
          </nav>

          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span className="app-header-role" style={rolePillStyle}>
              <span aria-hidden style={rolePillDotStyle} />
              Signed in as <strong style={{ color: color.heading }}>{humanise(user.role)}</strong>
            </span>

            <NotificationBell />

            {/* The rail carries identity on desktop; this is its narrow-screen
                counterpart, shown by the stylesheet below 820px. */}
            <div ref={menuRef} className="app-header-account" style={{ position: 'relative', display: 'none' }}>
              <button
                type="button"
                onClick={() => setMenuOpen((open) => !open)}
                aria-haspopup="menu"
                aria-expanded={menuOpen}
                aria-label={`Account menu for ${user.name}`}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  height: 34,
                  padding: '0 10px',
                  borderRadius: radius.control,
                  border: `1px solid ${color.borderInput}`,
                  background: color.surface,
                  cursor: 'pointer',
                  fontFamily: font.sans,
                }}
              >
                <span
                  aria-hidden
                  style={{
                    width: 22,
                    height: 22,
                    flexShrink: 0,
                    borderRadius: '50%',
                    background: color.brandSoft,
                    color: color.brandDark,
                    fontSize: 10,
                    fontWeight: 800,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  {initials(user.name)}
                </span>
                <span
                  className="app-header-name"
                  style={{ fontSize: 12.5, fontWeight: 700, color: color.heading }}
                >
                  {user.name}
                </span>
              </button>

              {menuOpen && (
                <div
                  role="menu"
                  style={{
                    position: 'absolute',
                    top: 'calc(100% + 6px)',
                    right: 0,
                    minWidth: 220,
                    background: color.surface,
                    border: `1px solid ${color.border}`,
                    borderRadius: radius.card,
                    boxShadow: '0 4px 14px rgba(16,24,40,.10)',
                    padding: 10,
                    zIndex: 130,
                  }}
                >
                  <div style={{ padding: '2px 4px 10px', borderBottom: `1px solid ${color.hairline}` }}>
                    <div style={{ fontSize: 12.5, fontWeight: 700, color: color.heading }}>{user.name}</div>
                    <div style={{ fontFamily: font.mono, fontSize: 11, color: color.faint, marginTop: 2 }}>
                      {user.email}
                    </div>
                    <div style={{ fontSize: 11, color: color.subtle, fontWeight: 600, marginTop: 4 }}>
                      {humanise(user.role)}
                      {user.team ? ` · ${user.team.name}` : ''}
                    </div>
                  </div>
                  <button
                    type="button"
                    role="menuitem"
                    onClick={logout}
                    style={{
                      marginTop: 8,
                      width: '100%',
                      height: 32,
                      borderRadius: radius.control,
                      border: `1px solid ${color.borderInput}`,
                      background: color.surface,
                      color: color.subtle,
                      fontFamily: font.sans,
                      fontSize: 12.5,
                      fontWeight: 700,
                      cursor: 'pointer',
                    }}
                  >
                    Sign out
                  </button>
                </div>
              )}
            </div>
          </div>
        </header>

        <main className="app-main" style={{ flex: 1, minWidth: 0, padding: size.pagePadding }}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}

/** Up to two initials, for the account button. */
function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('');
}

// ----------------------------------------------------------------- styles

const crumbListStyle = {
  display: 'flex',
  alignItems: 'center',
  gap: 8,
  margin: 0,
  padding: 0,
  listStyle: 'none',
  minWidth: 0,
} as const;

const sectionPillStyle = {
  display: 'inline-block',
  padding: '3px 9px',
  borderRadius: radius.pill,
  background: color.brandSoft,
  color: color.brandDark,
  fontFamily: font.mono,
  fontSize: 10,
  fontWeight: 600,
  letterSpacing: '0.09em',
  textTransform: 'uppercase',
  whiteSpace: 'nowrap',
} as const;

const crumbTitleStyle = {
  margin: 0,
  fontSize: 14.5,
  fontWeight: 800,
  color: color.heading,
  letterSpacing: '-0.2px',
  whiteSpace: 'nowrap',
  overflow: 'hidden',
  textOverflow: 'ellipsis',
} as const;

const rolePillStyle = {
  display: 'flex',
  alignItems: 'center',
  gap: 6,
  height: 30,
  padding: '0 11px',
  borderRadius: radius.pill,
  border: `1px solid ${color.border}`,
  background: color.thead,
  fontSize: 11.5,
  fontWeight: 600,
  color: color.subtle,
  whiteSpace: 'nowrap',
} as const;

const rolePillDotStyle = {
  width: 6,
  height: 6,
  borderRadius: '50%',
  background: '#16A34A',
  flexShrink: 0,
} as const;

const railUserStyle = {
  display: 'flex',
  alignItems: 'center',
  gap: 9,
  marginTop: 'auto',
  padding: '10px 10px',
  borderRadius: radius.control,
  background: 'rgba(255,255,255,.06)',
  border: '1px solid rgba(255,255,255,.08)',
} as const;

const railAvatarStyle = {
  width: 30,
  height: 30,
  flexShrink: 0,
  borderRadius: '50%',
  background: color.brand,
  color: '#FFFFFF',
  fontSize: 11,
  fontWeight: 800,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
} as const;

const railNameStyle = {
  display: 'block',
  fontSize: 12.5,
  fontWeight: 700,
  color: '#FFFFFF',
  whiteSpace: 'nowrap',
  overflow: 'hidden',
  textOverflow: 'ellipsis',
} as const;

const railRoleStyle = {
  display: 'block',
  fontSize: 10.5,
  fontWeight: 600,
  color: 'rgba(255,255,255,.45)',
  whiteSpace: 'nowrap',
  overflow: 'hidden',
  textOverflow: 'ellipsis',
} as const;

const railSignOutStyle = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  width: 30,
  height: 30,
  flexShrink: 0,
  borderRadius: radius.small,
  border: 'none',
  background: 'transparent',
  color: 'rgba(255,255,255,.55)',
  cursor: 'pointer',
} as const;
