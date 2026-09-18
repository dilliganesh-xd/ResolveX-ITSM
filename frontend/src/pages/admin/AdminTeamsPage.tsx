import { useCallback, useEffect, useState, type FormEvent } from 'react';
import {
  createTeam,
  listTeamMembers,
  listTeams,
  setTeamActive,
  updateTeam,
  type Team,
  type TeamMember,
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
import { RowActions } from '@/components/ui/RowActions';
import { SectionLabel } from '@/components/ui/Toolbar';
import { IconCheck, IconEdit, IconMembers, IconPower, IconTeam, IconUsers } from '@/components/ui/icons';
import { describeFailure } from '@/lib/adminForm';
import { formatDateTime, humanise } from '@/lib/display';
import { color, font, radius, size, tone } from '@/lib/tokens';

const ROLE_TONE = { ADMIN: 'purple', SUPPORT_AGENT: 'blue', EMPLOYEE: 'grey' } as const;

interface FormState {
  name: string;
  description: string;
  active: boolean;
}

const BLANK: FormState = { name: '', description: '', active: true };

/**
 * Teams.
 *
 * <p>A team is never deleted: incidents are routed to it and users belong to it,
 * so the row has to survive for that history to resolve. Deactivating is the
 * equivalent operation, and it is what the UI offers.
 */
export function AdminTeamsPage() {
  const { notify } = useToast();
  const [teams, setTeams] = useState<Team[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [formOpen, setFormOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<FormState>(BLANK);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const [pendingToggle, setPendingToggle] = useState<Team | null>(null);
  const [viewing, setViewing] = useState<Team | null>(null);
  const [members, setMembers] = useState<TeamMember[] | null>(null);

  const load = useCallback(() => {
    listTeams()
      .then((rows) => {
        setTeams(rows);
        setError(null);
      })
      .catch((caught) => setError(describeFailure(caught, 'Could not load teams.').message));
  }, []);

  useEffect(load, [load]);

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
        await updateTeam(editingId, {
          name: form.name.trim(),
          description: form.description.trim(),
          active: form.active,
        });
        announce('Team updated.');
      } else {
        await createTeam({ name: form.name.trim(), description: form.description.trim() });
        announce('Team created.');
      }
      setFormOpen(false);
      setEditingId(null);
      setForm(BLANK);
      load();
    } catch (caught) {
      const failure = describeFailure(caught, 'Could not save the team.');
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
      await setTeamActive(pendingToggle.id, !pendingToggle.active);
      announce(pendingToggle.active ? 'Team deactivated.' : 'Team activated.');
      load();
    } catch (caught) {
      fail(describeFailure(caught, 'Could not change the team.').message);
    } finally {
      setBusy(false);
      setPendingToggle(null);
    }
  }

  function openMembers(team: Team) {
    setViewing(team);
    setMembers(null);
    listTeamMembers(team.id)
      .then(setMembers)
      .catch(() => setMembers([]));
  }

  const loaded = teams ?? [];
  const activeCount = loaded.filter((row) => row.active).length;
  const memberCount = loaded.reduce((sum, row) => sum + row.memberCount, 0);

  return (
    <>
      <PageHeader
        title="Teams"
        subtitle={teams ? `${teams.length} team${teams.length === 1 ? '' : 's'}` : 'Loading teams'}
        action={
          <Button
            onClick={() => {
              setEditingId(null);
              setForm(BLANK);
              setFieldErrors({});
              setFormOpen((open) => !open);
            }}
          >
            {formOpen && !editingId ? 'Close' : 'New team'}
          </Button>
        }
      />

      {error && <ErrorBanner message={error} />}

      <div style={statGridStyle}>
        <StatCard label="Teams" value={loaded.length} icon={IconTeam} />
        <StatCard label="Active" value={activeCount} icon={IconCheck} />
        <StatCard label="People assigned" value={memberCount} icon={IconUsers} hint="Across all teams" />
      </div>

      <SectionLabel hint="Incidents route to a team before they reach a person">Teams</SectionLabel>

      {formOpen && (
        <div style={{ marginBottom: 14 }}>
          <Card title={editingId ? 'Edit team' : 'New team'}>
            <form onSubmit={onSubmit}>
              <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
                <Field label="Name" error={fieldErrors.name} grow>
                  <input
                    required
                    value={form.name}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                    placeholder="Infrastructure"
                    style={control}
                  />
                </Field>
                <Field label="Description" error={fieldErrors.description} grow>
                  <input
                    value={form.description}
                    onChange={(e) => setForm({ ...form, description: e.target.value })}
                    placeholder="Network, servers and access management"
                    style={control}
                  />
                </Field>
              </div>

              {editingId && (
                <label
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6,
                    fontSize: 12,
                    color: color.subtle,
                    fontWeight: 600,
                    margin: '0 0 12px',
                  }}
                >
                  <input
                    type="checkbox"
                    checked={form.active}
                    onChange={(e) => setForm({ ...form, active: e.target.checked })}
                  />
                  Active
                </label>
              )}

              <div style={{ display: 'flex', gap: 8 }}>
                <Button type="submit" disabled={busy || !form.name.trim()}>
                  {editingId ? 'Save team' : 'Create team'}
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
        {teams === null && !error && <TableSkeleton rows={4} columns={6} />}

        {teams !== null && teams.length === 0 && (
          <EmptyState
            title="No teams created yet"
            description="Create a team so incidents can be routed to a group rather than a single person."
            action={<Button onClick={() => setFormOpen(true)}>New team</Button>}
          />
        )}

        {teams !== null && teams.length > 0 && (
          <DataTable
            head={['Name', 'Description', 'Members', 'Status', 'Created', '']}
            rows={teams}
            rowKey={(row) => row.id}
            minWidth={820}
            rowTint={(row) => (row.active ? undefined : tone.grey.bg)}
            render={(row) => [
              <strong style={{ color: color.heading }}>{row.name}</strong>,
              row.description ?? <span style={{ color: color.faint }}>--</span>,
              <span style={{ fontFamily: font.mono, fontSize: 12 }}>{row.memberCount}</span>,
              <StatusPill label={row.active ? 'Active' : 'Inactive'} toneName={row.active ? 'green' : 'grey'} />,
              <span style={{ fontFamily: font.mono, fontSize: 11.5, color: color.subtle }}>
                {formatDateTime(row.createdAt)}
              </span>,
              <RowActions
                actions={[
                  { label: 'View members', icon: IconMembers, disabled: busy, onClick: () => openMembers(row) },
                  {
                    label: 'Edit team',
                    icon: IconEdit,
                    disabled: busy,
                    onClick: () => {
                      setEditingId(row.id);
                      setForm({ name: row.name, description: row.description ?? '', active: row.active });
                      setFieldErrors({});
                      setFormOpen(true);
                    },
                  },
                  {
                    label: row.active ? 'Deactivate team' : 'Activate team',
                    icon: IconPower,
                    intent: row.active ? 'danger' : 'neutral',
                    disabled: busy,
                    onClick: () => setPendingToggle(row),
                  },
                ]}
              />,
            ]}
          />
        )}
      </Card>

      {viewing && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label={`Members of ${viewing.name}`}
          onClick={() => setViewing(null)}
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,.45)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: 16,
            zIndex: 200,
          }}
        >
          <div onClick={(event) => event.stopPropagation()} style={{ width: '100%', maxWidth: 440 }}>
            <Card
              title={viewing.name}
              subtitle={`${viewing.memberCount} member${viewing.memberCount === 1 ? '' : 's'}`}
              action={
                <Button variant="secondary" style={compact} onClick={() => setViewing(null)}>
                  Close
                </Button>
              }
            >
              {members === null && <div style={{ color: color.muted, fontWeight: 600 }}>Loading...</div>}
              {members !== null && members.length === 0 && (
                <div style={{ color: color.faint, fontWeight: 600 }}>
                  Nobody belongs to this team yet. Assign someone from the Users page.
                </div>
              )}
              {members !== null && members.length > 0 && (
                <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'grid', gap: 8 }}>
                  {members.map((member) => (
                    <li
                      key={member.id}
                      style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}
                    >
                      <strong style={{ fontSize: 12.5, color: color.heading }}>{member.name}</strong>
                      <StatusPill label={humanise(member.role)} toneName={ROLE_TONE[member.role]} />
                      {!member.active && <StatusPill label="Inactive" toneName="grey" />}
                      <span style={{ fontFamily: font.mono, fontSize: 11, color: color.faint }}>
                        {member.email}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          </div>
        </div>
      )}

      {pendingToggle && (
        <ConfirmDialog
          title={pendingToggle.active ? 'Deactivate team' : 'Activate team'}
          message={
            pendingToggle.active
              ? `${pendingToggle.name} will stop being offered for new routing. Existing incidents and members keep their link to it — nothing is deleted.`
              : `${pendingToggle.name} will be available for routing again.`
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
    <div style={{ marginBottom: 14, flex: grow ? '1 1 220px' : '0 1 160px' }}>
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

const compact = { height: 30, fontSize: 12 } as const;

const statGridStyle = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fill, minmax(190px, 1fr))',
  gap: 12,
  marginBottom: 4,
} as const;
