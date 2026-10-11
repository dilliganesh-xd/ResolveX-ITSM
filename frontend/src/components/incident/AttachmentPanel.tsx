import { useCallback, useEffect, useRef, useState, type ChangeEvent } from 'react';
import axios from 'axios';
import { downloadAttachment, listAttachments, removeAttachment, uploadAttachment } from '@/api/incidents';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { IconDownload } from '@/components/ui/icons';
import { useToast } from '@/feedback/useToast';
import { formatDateTime } from '@/lib/display';
import { color, font, radius } from '@/lib/tokens';
import type { ApiError, Attachment, IncidentStatus } from '@/lib/types';

interface AttachmentPanelProps {
  incidentId: string;
  status: IncidentStatus;
  /** Only an admin may remove a file; the API enforces the same rule. */
  canRemove: boolean;
  /** Called after a file is added or removed, so the activity trail can refresh. */
  onChanged: () => void;
}

/**
 * Mirrors the server's allow-list so an obviously wrong file is caught before
 * it is uploaded. The server still checks every file - this is only feedback.
 */
const ACCEPT = '.png,.jpg,.jpeg,.gif,.webp,.pdf,.txt,.log,.csv,.docx,.xlsx';
const MAX_BYTES = 10 * 1024 * 1024;

export function AttachmentPanel({ incidentId, status, canRemove, onChanged }: AttachmentPanelProps) {
  const { notify } = useToast();
  const input = useRef<HTMLInputElement>(null);
  const [attachments, setAttachments] = useState<Attachment[]>([]);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pendingRemoval, setPendingRemoval] = useState<Attachment | null>(null);
  const [removing, setRemoving] = useState(false);

  const closed = status === 'CLOSED';

  const load = useCallback(() => {
    listAttachments(incidentId).then(setAttachments).catch(() => setAttachments([]));
  }, [incidentId]);

  useEffect(load, [load]);

  async function onFileChosen(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    // Cleared so choosing the same file again still fires a change.
    event.target.value = '';
    if (!file) {
      return;
    }
    const extension = file.name.includes('.') ? `.${file.name.split('.').pop()?.toLowerCase()}` : '';
    if (!ACCEPT.split(',').includes(extension)) {
      setError('Only PNG, JPG, GIF, WEBP, PDF, TXT, LOG, CSV, DOCX and XLSX files can be attached.');
      return;
    }
    if (file.size === 0) {
      setError('Empty files cannot be attached.');
      return;
    }
    if (file.size > MAX_BYTES) {
      setError('Files can be at most 10 MB.');
      return;
    }

    setUploading(true);
    setError(null);
    try {
      await uploadAttachment(incidentId, file);
      notify(`${file.name} attached.`);
      load();
      onChanged();
    } catch (caught) {
      setError(messageFrom(caught, 'Could not attach the file.'));
    } finally {
      setUploading(false);
    }
  }

  async function onDownload(attachment: Attachment) {
    try {
      await downloadAttachment(attachment);
    } catch {
      notify('Could not download the file.');
    }
  }

  async function onConfirmRemove() {
    if (!pendingRemoval) {
      return;
    }
    setRemoving(true);
    try {
      await removeAttachment(pendingRemoval);
      notify(`${pendingRemoval.fileName} removed.`);
      setPendingRemoval(null);
      load();
      onChanged();
    } catch (caught) {
      setError(messageFrom(caught, 'Could not remove the file.'));
      setPendingRemoval(null);
    } finally {
      setRemoving(false);
    }
  }

  return (
    <Card
      title="Attachments"
      subtitle={`${attachments.length} ${attachments.length === 1 ? 'file' : 'files'}`}
      action={
        closed ? undefined : (
          <>
            <input
              ref={input}
              type="file"
              accept={ACCEPT}
              onChange={onFileChosen}
              style={{ display: 'none' }}
              aria-label="Choose a file to attach"
            />
            <Button variant="secondary" disabled={uploading} onClick={() => input.current?.click()}>
              {uploading ? 'Uploading...' : 'Attach file'}
            </Button>
          </>
        )
      }
    >
      {attachments.length === 0 ? (
        <div style={{ color: color.faint, fontWeight: 600 }}>
          {closed ? 'No files were attached.' : 'No files yet. Screenshots, logs and documents up to 10 MB.'}
        </div>
      ) : (
        <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'grid', gap: 8 }}>
          {attachments.map((attachment) => (
            <li
              key={attachment.id}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                border: `1px solid ${color.hairline}`,
                borderRadius: radius.control,
                padding: '8px 10px',
              }}
            >
              <div style={{ minWidth: 0, flex: 1 }}>
                <button
                  type="button"
                  onClick={() => onDownload(attachment)}
                  title={`Download ${attachment.fileName}`}
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 6,
                    maxWidth: '100%',
                    padding: 0,
                    border: 'none',
                    background: 'none',
                    cursor: 'pointer',
                    color: color.brand,
                    fontFamily: font.sans,
                    fontSize: 12.5,
                    fontWeight: 700,
                    textAlign: 'left',
                  }}
                >
                  <IconDownload size={14} />
                  <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {attachment.fileName}
                  </span>
                </button>
                <div style={{ marginTop: 2, fontSize: 11, color: color.faint, fontWeight: 600 }}>
                  {formatSize(attachment.sizeBytes)} · {attachment.uploadedByName} ·{' '}
                  <span style={{ fontFamily: font.mono }}>{formatDateTime(attachment.createdAt)}</span>
                </div>
              </div>
              {canRemove && (
                <Button variant="secondary" onClick={() => setPendingRemoval(attachment)}>
                  Remove
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}

      {error && (
        <div role="alert" style={{ margin: '10px 0 0', fontSize: 12.5, fontWeight: 600, color: '#BE123C' }}>
          {error}
        </div>
      )}

      {pendingRemoval && (
        <ConfirmDialog
          title="Remove attachment"
          message={`Remove ${pendingRemoval.fileName} from this incident? The file is deleted and cannot be recovered.`}
          confirmLabel="Remove"
          busy={removing}
          onConfirm={onConfirmRemove}
          onCancel={() => setPendingRemoval(null)}
        />
      )}
    </Card>
  );
}

function messageFrom(caught: unknown, fallback: string): string {
  return axios.isAxiosError<ApiError>(caught) ? caught.response?.data?.message ?? fallback : fallback;
}

function formatSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${Math.round(bytes / 1024)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
