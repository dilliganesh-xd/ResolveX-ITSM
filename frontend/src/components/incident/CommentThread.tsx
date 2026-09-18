import { useState, type FormEvent } from 'react';
import axios from 'axios';
import { addComment } from '@/api/incidents';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { StatusPill } from '@/components/ui/StatusPill';
import { useToast } from '@/feedback/useToast';
import { formatDateTime, humanise } from '@/lib/display';
import { color, font, radius } from '@/lib/tokens';
import type { ApiError, Comment, IncidentStatus, RoleName } from '@/lib/types';

interface CommentThreadProps {
  incidentId: string;
  status: IncidentStatus;
  comments: Comment[];
  /** Only an agent or admin may mark a note internal. */
  canPostInternal: boolean;
  onPosted: () => void;
}

/** Roles are shown next to the author, so a reply's authority is visible. */
const ROLE_TONE: Record<RoleName, 'blue' | 'purple' | 'grey'> = {
  ADMIN: 'purple',
  SUPPORT_AGENT: 'blue',
  EMPLOYEE: 'grey',
};

export function CommentThread({
  incidentId,
  status,
  comments,
  canPostInternal,
  onPosted,
}: CommentThreadProps) {
  const { notify } = useToast();
  const [value, setValue] = useState('');
  const [internal, setInternal] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const closed = status === 'CLOSED';

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!value.trim()) {
      setError('Comment cannot be empty.');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await addComment(incidentId, value.trim(), internal);
      setValue('');
      setInternal(false);
      notify(internal ? 'Internal note added.' : 'Comment added.');
      onPosted();
    } catch (caught) {
      setError(
        axios.isAxiosError<ApiError>(caught)
          ? caught.response?.data?.fieldErrors?.comment ??
            caught.response?.data?.message ??
            'Could not post the comment.'
          : 'Could not post the comment.',
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Card title="Comments" subtitle={`${comments.length} on this incident`}>
      {comments.length === 0 ? (
        <div style={{ color: color.faint, fontWeight: 600, marginBottom: 14 }}>
          No comments yet. Add the first one below.
        </div>
      ) : (
        <ol style={{ margin: '0 0 16px', padding: 0, listStyle: 'none', display: 'grid', gap: 10 }}>
          {comments.map((comment) => (
            <li
              key={comment.id}
              style={{
                border: `1px solid ${comment.internal ? '#FED7AA' : color.hairline}`,
                background: comment.internal ? '#FFF7ED' : color.surface,
                borderRadius: radius.control,
                padding: '10px 12px',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                <strong style={{ fontSize: 12.5, color: color.heading }}>{comment.authorName}</strong>
                <StatusPill label={humanise(comment.authorRole)} toneName={ROLE_TONE[comment.authorRole]} />
                <span style={{ fontFamily: font.mono, fontSize: 10.5, color: color.faint }}>
                  {formatDateTime(comment.createdAt)}
                </span>
                {comment.internal && <StatusPill label="Internal" toneName="amber" />}
              </div>
              <p style={{ margin: '6px 0 0', fontSize: 12.5, color: color.body, whiteSpace: 'pre-wrap' }}>
                {comment.comment}
              </p>
            </li>
          ))}
        </ol>
      )}

      {closed ? (
        <div style={{ fontSize: 12.5, color: color.faint, fontWeight: 600 }}>
          This incident is closed. Reopen it to continue the conversation.
        </div>
      ) : (
        <form onSubmit={onSubmit}>
          <textarea
            rows={3}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            placeholder="Add a comment"
            aria-label="Add a comment"
            style={{
              width: '100%',
              padding: '9px 11px',
              border: `1px solid ${color.borderInput}`,
              borderRadius: radius.control,
              fontSize: 13,
              fontFamily: font.sans,
              color: color.heading,
              resize: 'vertical',
            }}
          />

          {error && (
            <div role="alert" style={{ margin: '8px 0 0', fontSize: 12.5, fontWeight: 600, color: '#BE123C' }}>
              {error}
            </div>
          )}

          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 8, flexWrap: 'wrap' }}>
            <Button type="submit" disabled={submitting || !value.trim()}>
              {submitting ? 'Posting...' : 'Submit comment'}
            </Button>
            {canPostInternal && (
              <label
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  fontSize: 12,
                  color: color.subtle,
                  fontWeight: 600,
                }}
              >
                <input type="checkbox" checked={internal} onChange={(e) => setInternal(e.target.checked)} />
                Internal note (hidden from the reporter)
              </label>
            )}
          </div>
        </form>
      )}
    </Card>
  );
}
