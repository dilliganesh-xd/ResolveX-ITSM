import { useCallback, useEffect, useState, type FormEvent } from 'react';
import axios from 'axios';
import {
  createSlaPolicy,
  listSlaPolicies,
  setSlaPolicyActive,
  updateSlaPolicy,
  type SlaPolicy,
  type SlaPolicyPayload,
} from '@/api/admin';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/EmptyState';
import { PageHeader } from '@/components/ui/PageHeader';
import { StatusPill } from '@/components/ui/StatusPill';
import { StatCard } from '@/components/ui/StatCard';
import { DataTable } from '@/components/ui/DataTable';
import { RowActions } from '@/components/ui/RowActions';
import { SectionLabel } from '@/components/ui/Toolbar';
import { IconAlert, IconCheck, IconClock, IconEdit, IconPower } from '@/components/ui/icons';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { TableSkeleton } from '@/components/ui/Skeleton';
import { useToast } from '@/feedback/useToast';
import { PRIORITY_TONE, formatDateTime } from '@/lib/display';
import { color, font, radius, size, tone } from '@/lib/tokens';
import type { ApiError, Priority } from '@/lib/types';

const PRIORITIES: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

const BLANK: SlaPolicyPayload = {
  name: '',
  priority: 'MEDIUM',
  responseTimeMinutes: 60,
  resolutionTimeMinutes: 1440,
  active: true,
};

/**
 * SLA policy management.
 *
 * <p>Exactly one policy is active per priority: activating one stands the others
 * down, server-side. Incidents already created keep the deadlines they were
 * stamped with, so editing a policy only affects incidents reported after it.
 */
export function AdminSlaPage() {
  const { notify } = useToast();
  const [policies, setPolicies] = useState<SlaPolicy[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [editingId, setEditingId] = useState<string | null>(null);
  const [pendingToggle, setPendingToggle] = useState<SlaPolicy | null>(null);
  const [form, setForm] = useState<SlaPolicyPayload>(BLANK);
  const [formOpen, setFormOpen] = useState(false);

  const load = useCallback(() => {
    listSlaPolicies()
      .then((rows) => {
        setPolicies(rows);
        setError(null);
      })
      .catch(() => setError('Could not load SLA policies.'));
  }, []);

  useEffect(load, [load]);

  async function run(action: () => Promise<unknown>) {
    setBusy(true);
    setError(null);
    try {
      await action();
      load();
      return true;
    } catch (caught) {
      setError(
        axios.isAxiosError<ApiError>(caught)
          ? caught.response?.data?.message ?? 'That change was rejected.'
          : 'That change was rejected.',
      );
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    const payload: SlaPolicyPayload = { ...form, name: form.name.trim() };
    const ok = await run(() =>
      editingId ? updateSlaPolicy(editingId, payload) : createSlaPolicy(payload),
    );
    if (ok) {
      notify(editingId ? 'SLA policy updated.' : 'SLA policy created.');
      setFormOpen(false);
      setEditingId(null);
      setForm(BLANK);
    }
  }

  function startEdit(policy: SlaPolicy) {
    setEditingId(policy.id);
    setForm({
      name: policy.name,
      priority: policy.priority,
      responseTimeMinutes: policy.responseTimeMinutes,
      resolutionTimeMinutes: policy.resolutionTimeMinutes,
      active: policy.active,
    });
    setFormOpen(true);
  }

  const prioritiesWithoutActive = PRIORITIES.filter(
    (priority) => policies !== null && !policies.some((p) => p.priority === priority && p.active),
  );

  const loaded = policies ?? [];
  const active = loaded.filter((policy) => policy.active);

  return (
    <>
      <PageHeader
        title="SLA Policies"
        subtitle="Response and resolution targets, applied when an incident is created. Editing a policy never moves an existing deadline."
        action={
          <Button
            onClick={() => {
              setEditingId(null);
              setForm(BLANK);
              setFormOpen((open) => !open);
            }}
          >
            {formOpen && !editingId ? 'Close' : 'New policy'}
          </Button>
        }
      />

      {error && (
        <div
          role="alert"
          style={{
            marginBottom: 12,
            padding: '9px 11px',
            borderRadius: radius.control,
            background: '#FEF2F2',
            color: '#BE123C',
            fontSize: 12.5,
            fontWeight: 600,
          }}
        >
          {error}
        </div>
      )}

      {/* The one thing this page exists to guarantee. A priority with no active
          policy silently creates incidents carrying no deadline at all, which
          stays invisible until somebody asks why nothing ever breached. */}
      {policies !== null && prioritiesWithoutActive.length > 0 && (
        <div role="status" style={coverageWarningStyle}>
          <span aria-hidden style={{ display: 'flex', marginTop: 1 }}>
            <IconAlert size={15} />
          </span>
          <span>
            <strong>No active policy for {prioritiesWithoutActive.join(', ')}.</strong> New incidents
            at {prioritiesWithoutActive.length === 1 ? 'that priority' : 'those priorities'} are
            created with no response or resolution deadline. Activate a policy to start the clocks.
          </span>
        </div>
      )}

      {formOpen && (
        <div style={{ marginBottom: 14 }}>
          <Card title={editingId ? 'Edit policy' : 'New policy'}>
            <form onSubmit={onSubmit}>
              <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
                <Field label="Name" grow>
                  <input
                    required
                    value={form.name}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                    placeholder="High - 30m / 8h"
                    style={control}
                  />
                </Field>

                <Field label="Priority">
                  <select
                    value={form.priority}
                    onChange={(e) => setForm({ ...form, priority: e.target.value as Priority })}
                    style={control}
                  >
                    {PRIORITIES.map((priority) => (
                      <option key={priority} value={priority}>
                        {priority}
                      </option>
                    ))}
                  </select>
                </Field>

                <Field label="Response (minutes)">
                  <input
                    required
                    type="number"
                    min={1}
                    value={form.responseTimeMinutes}
                    onChange={(e) => setForm({ ...form, responseTimeMinutes: Number(e.target.value) })}
                    style={control}
                  />
                </Field>

                <Field label="Resolution (minutes)">
                  <input
                    required
                    type="number"
                    min={1}
                    value={form.resolutionTimeMinutes}
                    onChange={(e) => setForm({ ...form, resolutionTimeMinutes: Number(e.target.value) })}
                    style={control}
                  />
                </Field>
              </div>

              <label
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  fontSize: 12,
                  color: color.subtle,
                  fontWeight: 600,
                  margin: '2px 0 14px',
                }}
              >
                <input
                  type="checkbox"
                  checked={form.active}
                  onChange={(e) => setForm({ ...form, active: e.target.checked })}
                />
                Active — this stands down any other active policy for {form.priority}
              </label>

              <div style={{ display: 'flex', gap: 8 }}>
                <Button type="submit" disabled={busy || !form.name.trim()}>
                  {editingId ? 'Save policy' : 'Create policy'}
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

      {pendingToggle && (
        <ConfirmDialog
          title={pendingToggle.active ? 'Deactivate policy' : 'Activate policy'}
          message={
            pendingToggle.active
              ? `New ${pendingToggle.priority} incidents will carry no SLA until another ${pendingToggle.priority} policy is active. Incidents already created keep their deadlines.`
              : `Activating this will deactivate any other active ${pendingToggle.priority} policy. Incidents already created keep the deadlines they were given.`
          }
          confirmLabel={pendingToggle.active ? 'Deactivate' : 'Activate'}
          busy={busy}
          onConfirm={() => {
            const target = pendingToggle;
            setPendingToggle(null);
            run(() => setSlaPolicyActive(target.id, !target.active)).then((ok) => {
              if (ok) {
                notify(target.active ? 'SLA policy deactivated.' : 'SLA policy activated.');
              }
            });
          }}
          onCancel={() => setPendingToggle(null)}
        />
      )}

      <div style={statGridStyle}>
        <StatCard label="Policies" value={loaded.length} icon={IconClock} />
        <StatCard label="Active" value={active.length} icon={IconCheck} />
        <StatCard
          label="Priorities covered"
          display={
            policies === null ? undefined : `${PRIORITIES.length - prioritiesWithoutActive.length}/${PRIORITIES.length}`
          }
          value={PRIORITIES.length - prioritiesWithoutActive.length}
          toneName={prioritiesWithoutActive.length > 0 ? 'amber' : undefined}
          icon={IconAlert}
        />
      </div>

      <SectionLabel hint="Exactly one policy is active per priority">Policies</SectionLabel>

      <Card>
        {policies === null && !error && <TableSkeleton rows={4} columns={8} />}

        {policies !== null && policies.length === 0 && (
          <EmptyState
            title="No SLA policies yet"
            description="Create one per priority so new incidents get a response and resolution deadline."
            action={<Button onClick={() => setFormOpen(true)}>New policy</Button>}
          />
        )}

        {policies !== null && policies.length > 0 && (
          <DataTable
            head={['Name', 'Priority', 'Response', 'Resolution', 'Status', 'Updated', '']}
            rows={policies}
            rowKey={(row) => row.id}
            minWidth={760}
            rowTint={(row) => (row.active ? undefined : tone.grey.bg)}
            render={(policy) => [
              <strong style={{ color: color.heading }}>{policy.name}</strong>,
              <StatusPill label={policy.priority} toneName={PRIORITY_TONE[policy.priority]} />,
              <span style={mono}>{formatMinutes(policy.responseTimeMinutes)}</span>,
              <span style={mono}>{formatMinutes(policy.resolutionTimeMinutes)}</span>,
              <StatusPill
                label={policy.active ? 'Active' : 'Inactive'}
                toneName={policy.active ? 'green' : 'grey'}
              />,
              <span style={mono}>{formatDateTime(policy.updatedAt)}</span>,
              <RowActions
                actions={[
                  { label: 'Edit policy', icon: IconEdit, disabled: busy, onClick: () => startEdit(policy) },
                  {
                    label: policy.active ? 'Deactivate policy' : 'Activate policy',
                    icon: IconPower,
                    intent: policy.active ? 'danger' : 'neutral',
                    disabled: busy,
                    onClick: () => setPendingToggle(policy),
                  },
                ]}
              />,
            ]}
          />
        )}
      </Card>
    </>
  );
}

function formatMinutes(minutes: number): string {
  if (minutes < 60) {
    return `${minutes}m`;
  }
  const hours = minutes / 60;
  return Number.isInteger(hours) ? `${hours}h` : `${hours.toFixed(1)}h`;
}

function Field({ label, grow, children }: { label: string; grow?: boolean; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 14, flex: grow ? '1 1 200px' : '0 1 150px' }}>
      <label style={{ display: 'block', marginBottom: 6, fontSize: 11.5, fontWeight: 700, color: color.subtle }}>
        {label}
      </label>
      {children}
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

const mono = {
  fontFamily: font.mono,
  fontSize: 11.5,
  color: color.body,
} as const;

const statGridStyle = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fill, minmax(190px, 1fr))',
  gap: 12,
  marginBottom: 4,
} as const;

const coverageWarningStyle = {
  display: 'flex',
  alignItems: 'flex-start',
  gap: 9,
  margin: '0 0 14px',
  padding: '11px 13px',
  borderRadius: radius.control,
  background: tone.amber.bg,
  border: `1px solid ${tone.amber.line}`,
  color: tone.amber.fg,
  fontSize: 12.5,
  fontWeight: 600,
  lineHeight: 1.55,
} as const;
