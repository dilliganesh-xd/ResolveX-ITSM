import { useCallback, useEffect, useState, type FormEvent } from 'react';
import {
  createUser,
  getUser,
  listTeams,
  listUsers,
  resetUserPassword,
  setUserActive,
  updateUser,
  type AdminUser,
  type AdminUserDetail,
  type Team,
  type UserFilters,
} from '@/api/admin';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { DataTable } from '@/components/ui/DataTable';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorBanner } from '@/components/ui/Feedback';
import { TableSkeleton } from '@/components/ui/Skeleton';
import { useToast } from '@/feedback/useToast';
import { PageHeader } from '@/components/ui/PageHeader';
import { StatusPill } from '@/components/ui/StatusPill';
import { StatCard } from '@/components/ui/StatCard';
import { PersonCell, RowActions } from '@/components/ui/RowActions';
import { SectionLabel } from '@/components/ui/Toolbar';
import { IconCheck, IconEdit, IconEye, IconKey, IconPower, IconShield, IconTeam, IconUsers } from '@/components/ui/icons';
import { useAuth } from '@/auth/useAuth';
import { describeFailure } from '@/lib/adminForm';
import { formatDateTime, humanise } from '@/lib/display';
import { color, font, radius, size, tone } from '@/lib/tokens';
import type { RoleName } from '@/lib/types';

const ROLES: RoleName[] = ['EMPLOYEE', 'SUPPORT_AGENT', 'ADMIN'];
const PAGE_SIZE = 25;

const ROLE_TONE = { ADMIN: 'purple', SUPPORT_AGENT: 'blue', EMPLOYEE: 'grey' } as const;

interface FormState {
  name: string;
  email: string;
  password: string;
  role: RoleName;
  teamId: string;
  active: boolean;
}

const BLANK: FormState = {
  name: '',
  email: '',
  password: '',
  role: 'EMPLOYEE',
  teamId: '',
  active: true,
};

export function AdminUsersPage() {
  const { user: currentUser } = useAuth();
  const { notify } = useToast();

  const [users, setUsers] = useState<AdminUser[] | null>(null);
  const [teams, setTeams] = useState<Team[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [filters, setFilters] = useState<UserFilters>({});

  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [formOpen, setFormOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<FormState>(BLANK);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [hadTeam, setHadTeam] = useState(false);

  const [pendingToggle, setPendingToggle] = useState<AdminUser | null>(null);
  const [details, setDetails] = useState<AdminUserDetail | null>(null);
  const [resetting, setResetting] = useState<AdminUser | null>(null);
  const [newPassword, setNewPassword] = useState('');

  const load = useCallback((applied: UserFilters, pageNumber: number) => {
    listUsers(applied, pageNumber, PAGE_SIZE)
      .then((result) => {
        setUsers(result.content);
        setTotal(result.totalElements);
        setTotalPages(result.totalPages);
        setPage(result.page);
        setError(null);
      })
      .catch((caught) => setError(describeFailure(caught, 'Could not load users.').message));
  }, []);

  // Search runs on a short debounce so typing does not fire a request per key.
  useEffect(() => {
    const timer = setTimeout(() => load(filters, 0), 250);
    return () => clearTimeout(timer);
  }, [filters, load]);

  useEffect(() => {
    listTeams().then(setTeams).catch(() => setTeams([]));
  }, []);

  // Derived from the rows in hand rather than fetched separately, so the tiles
  // can never disagree with the table under them. When the list is paged, the
  // hint says so instead of implying these are estate-wide totals.
  const loaded = users ?? [];
  const counts = {
    active: loaded.filter((row) => row.active).length,
    agents: loaded.filter((row) => row.role === 'SUPPORT_AGENT').length,
    admins: loaded.filter((row) => row.role === 'ADMIN').length,
  };
  const paged = users !== null && total > loaded.length;
  const scope = paged ? 'on this page' : undefined;

  /** One toast system for the whole product; failures also stay on the page. */
  function announce(message: string) {
    setError(null);
    notify(message);
  }

  function fail(message: string) {
    setError(message);
    notify(message, 'error');
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setFieldErrors({});
    try {
      if (editingId) {
        await updateUser(editingId, {
          name: form.name.trim(),
          email: form.email.trim(),
          role: form.role,
          teamId: form.teamId || undefined,
          // Removing a team has to be said explicitly; an absent id means "leave it".
          clearTeam: hadTeam && !form.teamId,
          active: form.active,
        });
        announce('User updated.');
      } else {
        await createUser({
          name: form.name.trim(),
          email: form.email.trim(),
          password: form.password,
          role: form.role,
          teamId: form.teamId || undefined,
        });
        announce('User created.');
      }
      setFormOpen(false);
      setEditingId(null);
      setForm(BLANK);
      load(filters, page);
    } catch (caught) {
      const failure = describeFailure(caught, 'Could not save the user.');
      fail(failure.message);
      setFieldErrors(failure.fieldErrors);
    } finally {
      setBusy(false);
    }
  }

  async function confirmToggle() {
    if (!pendingToggle) {
      return;
    }
    setBusy(true);
    try {
      await setUserActive(pendingToggle.id, !pendingToggle.active);
      announce(pendingToggle.active ? 'User deactivated.' : 'User activated.');
      load(filters, page);
    } catch (caught) {
      fail(describeFailure(caught, 'Could not change the user.').message);
    } finally {
      setBusy(false);
      setPendingToggle(null);
    }
  }

  async function submitReset() {
    if (!resetting || newPassword.length < 8) {
      return;
    }
    setBusy(true);
    try {
      await resetUserPassword(resetting.id, newPassword);
      announce('Password reset successfully.');
    } catch (caught) {
      fail(describeFailure(caught, 'Could not reset the password.').message);
    } finally {
      setBusy(false);
      setResetting(null);
      // Cleared immediately: the value has done its job and should not linger
      // in component state any longer than the request takes.
      setNewPassword('');
    }
  }

  function startEdit(user: AdminUser) {
    setEditingId(user.id);
    setHadTeam(Boolean(user.team));
    setForm({
      name: user.name,
      email: user.email,
      password: '',
      role: user.role,
      teamId: user.team?.id ?? '',
      active: user.active,
    });
    setFieldErrors({});
    setFormOpen(true);
  }

  const filtering = Boolean(filters.search || filters.role || filters.teamId || filters.active);

  return (
    <>
      <PageHeader
        title="Users"
        subtitle={users ? `${total} account${total === 1 ? '' : 's'}` : 'Loading users'}
        action={
          <Button
            onClick={() => {
              setEditingId(null);
              setForm(BLANK);
              setFieldErrors({});
              setFormOpen((open) => !open);
            }}
          >
            {formOpen && !editingId ? 'Close' : 'New user'}
          </Button>
        }
      />

      {error && <ErrorBanner message={error} />}

      {/* The shape of the directory, before the rows. Counted from the page in
          hand, so the figures always agree with the table below them. */}
      <div style={statGridStyle}>
        <StatCard label="Accounts" value={total} icon={IconUsers} />
        <StatCard label="Active" value={counts.active} hint={scope} icon={IconCheck} />
        <StatCard label="Support agents" value={counts.agents} hint={scope} icon={IconTeam} />
        <StatCard label="Administrators" value={counts.admins} hint={scope} icon={IconShield} />
      </div>

      <SectionLabel hint="Search, then narrow by role, team or status">Directory</SectionLabel>

      <form
        onSubmit={(event) => event.preventDefault()}
        style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center', marginBottom: 14 }}
      >
        <input
          type="search"
          placeholder="Search name or email"
          value={filters.search ?? ''}
          onChange={(e) => setFilters({ ...filters, search: e.target.value })}
          aria-label="Search users"
          style={{ ...filterControl, flex: '1 1 240px' }}
        />
        <select
          value={filters.role ?? ''}
          onChange={(e) => setFilters({ ...filters, role: e.target.value as RoleName | '' })}
          aria-label="Filter by role"
          style={filterControl}
        >
          <option value="">All roles</option>
          {ROLES.map((role) => (
            <option key={role} value={role}>
              {humanise(role)}
            </option>
          ))}
        </select>
        <select
          value={filters.teamId ?? ''}
          onChange={(e) => setFilters({ ...filters, teamId: e.target.value })}
          aria-label="Filter by team"
          style={filterControl}
        >
          <option value="">All teams</option>
          {teams.map((team) => (
            <option key={team.id} value={team.id}>
              {team.name}
            </option>
          ))}
        </select>
        <select
          value={filters.active ?? ''}
          onChange={(e) => setFilters({ ...filters, active: e.target.value as 'true' | 'false' | '' })}
          aria-label="Filter by status"
          style={filterControl}
        >
          <option value="">Active and inactive</option>
          <option value="true">Active only</option>
          <option value="false">Inactive only</option>
        </select>
        {filtering && (
          <Button type="button" variant="secondary" onClick={() => setFilters({})}>
            Clear filters
          </Button>
        )}
      </form>

      {formOpen && (
        <div style={{ marginBottom: 14 }}>
          <Card title={editingId ? 'Edit user' : 'New user'}>
            <form onSubmit={onSubmit}>
              <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
                <Field label="Name" error={fieldErrors.name} grow>
                  <input
                    required
                    value={form.name}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                    style={control}
                  />
                </Field>
                <Field label="Email" error={fieldErrors.email} grow>
                  <input
                    required
                    type="email"
                    value={form.email}
                    onChange={(e) => setForm({ ...form, email: e.target.value })}
                    style={control}
                  />
                </Field>
                {!editingId && (
                  <Field label="Password" error={fieldErrors.password} grow>
                    <input
                      required
                      type="password"
                      minLength={8}
                      autoComplete="new-password"
                      value={form.password}
                      onChange={(e) => setForm({ ...form, password: e.target.value })}
                      style={control}
                    />
                  </Field>
                )}
                <Field label="Role" error={fieldErrors.role}>
                  <select
                    value={form.role}
                    onChange={(e) => setForm({ ...form, role: e.target.value as RoleName })}
                    style={control}
                  >
                    {ROLES.map((role) => (
                      <option key={role} value={role}>
                        {humanise(role)}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label="Team" error={fieldErrors.teamId}>
                  <select
                    value={form.teamId}
                    onChange={(e) => setForm({ ...form, teamId: e.target.value })}
                    style={control}
                  >
                    <option value="">No team</option>
                    {teams.map((team) => (
                      <option key={team.id} value={team.id}>
                        {team.name}
                      </option>
                    ))}
                  </select>
                </Field>
              </div>

              {editingId && (
                <label style={checkboxLabel}>
                  <input
                    type="checkbox"
                    checked={form.active}
                    onChange={(e) => setForm({ ...form, active: e.target.checked })}
                  />
                  Active
                </label>
              )}

              {editingId && (
                <p style={{ margin: '0 0 12px', fontSize: 11.5, color: color.faint, fontWeight: 600 }}>
                  Editing a profile never changes a password. Resetting one is a separate operation.
                </p>
              )}

              <div style={{ display: 'flex', gap: 8 }}>
                <Button type="submit" disabled={busy || !form.name.trim() || !form.email.trim()}>
                  {editingId ? 'Save user' : 'Create user'}
                </Button>
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => {
                    setFormOpen(false);
                    setEditingId(null);
                    setForm(BLANK);
                  }}
                >
                  Cancel
                </Button>
              </div>
            </form>
          </Card>
        </div>
      )}

      <Card>
        {users === null && !error && <TableSkeleton rows={5} columns={7} />}

        {users !== null && users.length === 0 && (
          <EmptyState
            title={filtering ? 'No users match' : 'No users found'}
            description={
              filtering
                ? 'Try a different search, role, team or status.'
                : 'Create the first account to get started.'
            }
            action={
              filtering ? (
                <Button variant="secondary" onClick={() => setFilters({})}>
                  Clear filters
                </Button>
              ) : (
                <Button onClick={() => setFormOpen(true)}>New user</Button>
              )
            }
          />
        )}

        {users !== null && users.length > 0 && (
          <>
            <DataTable
              head={['Name', 'Role', 'Team', 'Status', 'Created', '']}
              rows={users}
              rowKey={(row) => row.id}
              minWidth={780}
              rowTint={(row) => (row.active ? undefined : tone.grey.bg)}
              render={(row) => [
                // Name and email in one cell: they identify the same person, and
                // a separate email column was the widest thing on the table.
                <PersonCell
                  name={row.name}
                  secondary={row.email}
                  note={
                    row.id === currentUser?.userId ? (
                      <span style={{ color: color.faint, fontWeight: 600 }}> (you)</span>
                    ) : undefined
                  }
                />,
                <StatusPill label={humanise(row.role)} toneName={ROLE_TONE[row.role]} />,
                row.team?.name ?? <span style={{ color: color.faint }}>--</span>,
                <StatusPill
                  label={row.active ? 'Active' : 'Inactive'}
                  toneName={row.active ? 'green' : 'grey'}
                />,
                <span style={{ fontFamily: font.mono, fontSize: 11.5, color: color.subtle }}>
                  {formatDateTime(row.createdAt)}
                </span>,
                <RowActions
                  actions={[
                    {
                      label: 'View details',
                      icon: IconEye,
                      disabled: busy,
                      onClick: () => getUser(row.id).then(setDetails).catch(() => undefined),
                    },
                    { label: 'Edit user', icon: IconEdit, disabled: busy, onClick: () => startEdit(row) },
                    {
                      label: 'Reset password',
                      icon: IconKey,
                      disabled: busy,
                      onClick: () => {
                        setResetting(row);
                        setNewPassword('');
                      },
                    },
                    {
                      label: row.active ? 'Deactivate user' : 'Activate user',
                      icon: IconPower,
                      intent: row.active ? 'danger' : 'neutral',
                      // An admin cannot lock themselves out; the API refuses it too.
                      disabled: busy || row.id === currentUser?.userId,
                      onClick: () => setPendingToggle(row),
                    },
                  ]}
                />,
              ]}
            />

            {totalPages > 1 && (
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 10,
                  marginTop: 12,
                  justifyContent: 'flex-end',
                }}
              >
                <span style={{ fontFamily: font.mono, fontSize: 11.5, color: color.subtle }}>
                  Page {page + 1} of {totalPages}
                </span>
                <Button
                  variant="secondary"
                  style={compact}
                  disabled={page === 0}
                  onClick={() => load(filters, page - 1)}
                >
                  Previous
                </Button>
                <Button
                  variant="secondary"
                  style={compact}
                  disabled={page + 1 >= totalPages}
                  onClick={() => load(filters, page + 1)}
                >
                  Next
                </Button>
              </div>
            )}
          </>
        )}
      </Card>

      {details && <UserDetails detail={details} onClose={() => setDetails(null)} />}

      {resetting && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label={`Reset password for ${resetting.name}`}
          onClick={() => setResetting(null)}
          style={overlay}
        >
          <div onClick={(event) => event.stopPropagation()} style={{ width: '100%', maxWidth: 400 }}>
            <Card title="Reset password" subtitle={resetting.name}>
              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  submitReset();
                }}
              >
                <p style={{ margin: '0 0 12px', fontSize: 12.5, color: color.muted, fontWeight: 500 }}>
                  {resetting.name} will sign in with this password from now on. Their old password stops
                  working immediately, so tell them what it is.
                </p>
                <Field label="New password">
                  <input
                    required
                    autoFocus
                    type="password"
                    minLength={8}
                    autoComplete="new-password"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    style={control}
                  />
                </Field>
                <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                  <Button type="button" variant="secondary" onClick={() => setResetting(null)} disabled={busy}>
                    Cancel
                  </Button>
                  <Button type="submit" disabled={busy || newPassword.length < 8}>
                    {busy ? 'Resetting...' : 'Reset password'}
                  </Button>
                </div>
              </form>
            </Card>
          </div>
        </div>
      )}

      {pendingToggle && (
        <ConfirmDialog
          title={pendingToggle.active ? 'Deactivate user' : 'Activate user'}
          message={
            pendingToggle.active
              ? `${pendingToggle.name} will no longer be able to sign in, and will not be offered for new assignments. Their incidents and history are kept.`
              : `${pendingToggle.name} will be able to sign in again.`
          }
          confirmLabel={pendingToggle.active ? 'Deactivate' : 'Activate'}
          busy={busy}
          onConfirm={confirmToggle}
          onCancel={() => setPendingToggle(null)}
        />
      )}
    </>
  );
}

function UserDetails({ detail, onClose }: { detail: AdminUserDetail; onClose: () => void }) {
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={`Details for ${detail.name}`}
      onClick={onClose}
      style={overlay}
    >
      <div onClick={(event) => event.stopPropagation()} style={{ width: '100%', maxWidth: 420 }}>
        <Card title={detail.name} subtitle={detail.email} action={<Button variant="secondary" style={compact} onClick={onClose}>Close</Button>}>
          <dl style={{ margin: 0, display: 'grid', gap: 10 }}>
            <DetailRow label="Role" value={humanise(detail.role)} />
            <DetailRow label="Team" value={detail.team?.name ?? '--'} />
            <DetailRow label="Status" value={detail.active ? 'Active' : 'Inactive'} />
            <DetailRow label="Created" value={formatDateTime(detail.createdAt)} />
            <DetailRow label="Updated" value={formatDateTime(detail.updatedAt)} />
            <DetailRow label="Assigned incidents" value={String(detail.assignedIncidentCount)} />
            <DetailRow label="Resolved or closed" value={String(detail.resolvedIncidentCount)} />
          </dl>
        </Card>
      </div>
    </div>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ display: 'flex', gap: 10, alignItems: 'baseline' }}>
      <dt style={{ minWidth: 140, flexShrink: 0, fontSize: 11.5, fontWeight: 700, color: color.subtle }}>
        {label}
      </dt>
      <dd style={{ margin: 0, fontSize: 12.5, fontWeight: 600, color: color.heading }}>{value}</dd>
    </div>
  );
}

function Field({
  label,
  error,
  grow,
  children,
}: {
  label: string;
  error?: string;
  grow?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div style={{ marginBottom: 14, flex: grow ? '1 1 190px' : '0 1 160px' }}>
      <label style={{ display: 'block', marginBottom: 6, fontSize: 11.5, fontWeight: 700, color: color.subtle }}>
        {label}
      </label>
      {children}
      {error && <div style={{ marginTop: 4, fontSize: 11.5, fontWeight: 600, color: '#BE123C' }}>{error}</div>}
    </div>
  );
}

const control = {
  width: '100%',
  height: size.control,
  padding: '0 11px',
  border: `1px solid ${color.borderInput}`,
  borderRadius: radius.control,
  fontSize: 13,
  fontFamily: font.sans,
  background: color.surface,
  color: color.heading,
} as const;

/**
 * The same control, sized for a filter row rather than a form field.
 *
 * <p>`control` is width:100% because in the create/edit form each input fills
 * its column. In a wrapping flex row that makes every filter claim the whole
 * line and stack, so the row needs its own basis.
 */
const filterControl = { ...control, width: 'auto', flex: '0 1 190px' } as const;

const compact = { height: 30, fontSize: 12 } as const;

const overlay = {
  position: 'fixed',
  inset: 0,
  background: 'rgba(0,0,0,.45)',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  padding: 16,
  zIndex: 200,
} as const;

const checkboxLabel = {
  display: 'flex',
  alignItems: 'center',
  gap: 6,
  fontSize: 12,
  color: color.subtle,
  fontWeight: 600,
  margin: '0 0 12px',
} as const;

const statGridStyle = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fill, minmax(190px, 1fr))',
  gap: 12,
  marginBottom: 4,
} as const;
