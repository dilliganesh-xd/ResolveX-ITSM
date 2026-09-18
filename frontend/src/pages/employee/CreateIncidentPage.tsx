import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { createIncident } from '@/api/incidents';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { PageHeader } from '@/components/ui/PageHeader';
import { useToast } from '@/feedback/useToast';
import { color, font, radius, size } from '@/lib/tokens';
import type { ApiError, Priority } from '@/lib/types';

const PRIORITIES: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const CATEGORIES = ['Network', 'Hardware', 'Software', 'Access', 'Email', 'Other'];

const TITLE_MAX = 160;
const DESCRIPTION_MAX = 5000;

export function CreateIncidentPage() {
  const navigate = useNavigate();
  const { notify } = useToast();
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState(CATEGORIES[0] as string);
  const [subcategory, setSubcategory] = useState('');
  const [priority, setPriority] = useState<Priority>('MEDIUM');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    setFieldErrors({});
    try {
      const created = await createIncident({
        title: title.trim(),
        description: description.trim(),
        category,
        subcategory: subcategory.trim() || undefined,
        priority,
      });
      // Names the incident, so the reporter leaves with the reference they will
      // be asked for if they ever chase it.
      notify('Incident ' + created.reference + ' created successfully.');
      navigate('/employee');
    } catch (caught) {
      if (axios.isAxiosError<ApiError>(caught)) {
        setError(caught.response?.data?.message ?? 'Could not create the incident.');
        setFieldErrors(caught.response?.data?.fieldErrors ?? {});
      } else {
        setError('Something went wrong. Please try again.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <PageHeader title="Report an incident" subtitle="Describe what is wrong and how urgent it is" />

      <div style={{ maxWidth: 640 }}>
        <Card>
          <form onSubmit={onSubmit}>
            <Field label="Title" error={fieldErrors.title} required hint={title.length + '/' + TITLE_MAX}>
              <input
                required
                maxLength={TITLE_MAX}
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="VPN disconnects every few minutes"
                style={controlStyle}
              />
            </Field>

            <Field
              label="Description"
              error={fieldErrors.description}
              required
              hint={description.length + '/' + DESCRIPTION_MAX}
            >
              <textarea
                required
                rows={5}
                maxLength={DESCRIPTION_MAX}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="What happened, when it started, and what you already tried"
                style={{ ...controlStyle, height: 'auto', padding: '9px 11px', resize: 'vertical' }}
              />
            </Field>

            <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
              <Field label="Category" error={fieldErrors.category} grow required>
                <select value={category} onChange={(e) => setCategory(e.target.value)} style={controlStyle}>
                  {CATEGORIES.map((c) => (
                    <option key={c} value={c}>
                      {c}
                    </option>
                  ))}
                </select>
              </Field>

              <Field label="Subcategory (optional)" error={fieldErrors.subcategory} grow>
                <input value={subcategory} onChange={(e) => setSubcategory(e.target.value)} style={controlStyle} />
              </Field>

              <Field label="Priority" error={fieldErrors.priority} grow required>
                <select
                  value={priority}
                  onChange={(e) => setPriority(e.target.value as Priority)}
                  style={controlStyle}
                >
                  {PRIORITIES.map((p) => (
                    <option key={p} value={p}>
                      {p}
                    </option>
                  ))}
                </select>
              </Field>
            </div>

            {error && (
              <div
                role="alert"
                style={{
                  margin: '4px 0 14px',
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

            <div style={{ display: 'flex', gap: 8 }}>
              <Button type="submit" disabled={submitting}>
                {submitting ? 'Submitting...' : 'Submit incident'}
              </Button>
              <Button type="button" variant="secondary" onClick={() => navigate('/employee')}>
                Cancel
              </Button>
            </div>
          </form>
        </Card>
      </div>
    </>
  );
}

const controlStyle = {
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

function Field({
  label,
  error,
  grow,
  required,
  hint,
  children,
}: {
  label: string;
  error?: string;
  grow?: boolean;
  /** Marked with an asterisk, and spelled out for assistive technology. */
  required?: boolean;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <div style={{ marginBottom: 14, flex: grow ? '1 1 160px' : undefined }}>
      <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 8 }}>
        <label
          style={{ display: 'block', marginBottom: 6, fontSize: 11.5, fontWeight: 700, color: color.subtle }}
        >
          {label}
          {required && (
            <>
              <span aria-hidden style={{ color: '#BE123C' }}> *</span>
              <span className="sr-only"> (required)</span>
            </>
          )}
        </label>
        {hint && <span style={{ fontFamily: font.mono, fontSize: 10.5, color: color.faint }}>{hint}</span>}
      </div>
      {children}
      {error && (
        <div role="alert" style={{ marginTop: 4, fontSize: 11.5, fontWeight: 600, color: '#BE123C' }}>
          {error}
        </div>
      )}
    </div>
  );
}
