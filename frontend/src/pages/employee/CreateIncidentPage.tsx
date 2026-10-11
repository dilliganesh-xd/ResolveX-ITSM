import { useRef, useState, type ChangeEvent, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { createIncident, uploadAttachment } from '@/api/incidents';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { PageHeader } from '@/components/ui/PageHeader';
import { useToast } from '@/feedback/useToast';
import { ATTACHMENT_ACCEPT, ATTACHMENT_TYPES_LABEL, attachmentProblem, formatFileSize } from '@/lib/attachments';
import { color, font, radius, size } from '@/lib/tokens';
import type { ApiError, IncidentDetail, Priority } from '@/lib/types';

const PRIORITIES: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const CATEGORIES = ['Network', 'Hardware', 'Software', 'Access', 'Email', 'Other'];

const TITLE_MAX = 160;
const DESCRIPTION_MAX = 5000;

/** Enough for screenshots and a log; more belongs on the incident afterwards. */
const MAX_FILES = 5;

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

  const [files, setFiles] = useState<File[]>([]);
  const [fileError, setFileError] = useState<string | null>(null);
  const [progress, setProgress] = useState<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  function onFilesChosen(event: ChangeEvent<HTMLInputElement>) {
    const chosen = Array.from(event.target.files ?? []);
    // Cleared so choosing the same file again still fires a change.
    event.target.value = '';
    const problems = chosen.map(attachmentProblem).filter((p): p is string => p !== null);
    const accepted = chosen.filter((file) => attachmentProblem(file) === null);
    const next = [...files, ...accepted].slice(0, MAX_FILES);
    if (files.length + accepted.length > MAX_FILES) {
      problems.push(`You can attach up to ${MAX_FILES} files.`);
    }
    setFiles(next);
    setFileError(problems.length ? problems.join(' ') : null);
  }

  function removeFile(index: number) {
    setFiles(files.filter((_, i) => i !== index));
    setFileError(null);
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    setFieldErrors({});

    let created: IncidentDetail;
    try {
      created = await createIncident({
        title: title.trim(),
        description: description.trim(),
        category,
        subcategory: subcategory.trim() || undefined,
        priority,
      });
    } catch (caught) {
      if (axios.isAxiosError<ApiError>(caught)) {
        setError(caught.response?.data?.message ?? 'Could not create the incident.');
        setFieldErrors(caught.response?.data?.fieldErrors ?? {});
      } else {
        setError('Something went wrong. Please try again.');
      }
      setSubmitting(false);
      return;
    }

    // The incident exists from here on. Files are attached to it one by one; one
    // that fails does not undo the incident or stop the others.
    const failed: string[] = [];
    for (const [index, file] of files.entries()) {
      setProgress(`Uploading file ${index + 1} of ${files.length}...`);
      try {
        await uploadAttachment(created.id, file);
      } catch {
        failed.push(file.name);
      }
    }
    setProgress(null);
    setSubmitting(false);

    if (failed.length > 0) {
      // Straight to the incident, where the Attachments card can retry them.
      notify(
        `Incident ${created.reference} was created, but ${failed.join(', ')} could not be attached. ` +
          'Try again from the Attachments section.',
        'error',
      );
      navigate(`/incidents/${created.id}`);
      return;
    }
    // Names the incident, so the reporter leaves with the reference they will
    // be asked for if they ever chase it.
    const attachedNote =
      files.length === 0 ? '' : ` with ${files.length} ${files.length === 1 ? 'attachment' : 'attachments'}`;
    notify('Incident ' + created.reference + ' created successfully' + attachedNote + '.');
    navigate('/employee');
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

            <Field
              label="Attachments (optional)"
              hint={`${files.length}/${MAX_FILES} · up to 10 MB each`}
            >
              <input
                ref={fileInput}
                type="file"
                multiple
                accept={ATTACHMENT_ACCEPT}
                onChange={onFilesChosen}
                style={{ display: 'none' }}
                aria-label="Choose files to attach"
              />
              <div
                style={{
                  border: `1px dashed ${color.borderInput}`,
                  borderRadius: radius.control,
                  padding: 10,
                  display: 'grid',
                  gap: 8,
                }}
              >
                {files.length > 0 && (
                  <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'grid', gap: 6 }}>
                    {files.map((file, index) => (
                      <li
                        key={`${file.name}-${index}`}
                        style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12.5 }}
                      >
                        <span
                          style={{
                            flex: 1,
                            minWidth: 0,
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                            fontWeight: 600,
                            color: color.heading,
                          }}
                        >
                          {file.name}
                        </span>
                        <span style={{ fontFamily: font.mono, fontSize: 11, color: color.faint }}>
                          {formatFileSize(file.size)}
                        </span>
                        <button
                          type="button"
                          onClick={() => removeFile(index)}
                          disabled={submitting}
                          aria-label={`Remove ${file.name}`}
                          style={{
                            border: 'none',
                            background: 'none',
                            cursor: 'pointer',
                            color: color.subtle,
                            fontSize: 16,
                            lineHeight: 1,
                            padding: '0 4px',
                          }}
                        >
                          ×
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
                  <Button
                    type="button"
                    variant="secondary"
                    disabled={submitting || files.length >= MAX_FILES}
                    onClick={() => fileInput.current?.click()}
                  >
                    {files.length === 0 ? 'Attach files' : 'Add more files'}
                  </Button>
                  <span style={{ fontSize: 11.5, color: color.faint }}>
                    Screenshots, logs or documents: {ATTACHMENT_TYPES_LABEL}
                  </span>
                </div>
              </div>
              {fileError && (
                <div role="alert" style={{ marginTop: 4, fontSize: 11.5, fontWeight: 600, color: '#BE123C' }}>
                  {fileError}
                </div>
              )}
            </Field>

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
                {progress ?? (submitting ? 'Submitting...' : 'Submit incident')}
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
