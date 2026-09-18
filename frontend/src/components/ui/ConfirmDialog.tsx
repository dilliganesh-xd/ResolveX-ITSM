import { Button } from './Button';
import { color, radius, shadow } from '@/lib/tokens';

interface ConfirmDialogProps {
  title: string;
  message: string;
  confirmLabel: string;
  busy?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

/**
 * Confirmation for a state-changing action.
 *
 * <p>Deliberately modest: a scrim, a card, two buttons. The confirm button is
 * the primary one because the person arrived here by choosing the action; the
 * dialog exists to make it deliberate, not to argue with them.
 */
export function ConfirmDialog({
  title,
  message,
  confirmLabel,
  busy,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={title}
      onClick={onCancel}
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
      <div
        onClick={(event) => event.stopPropagation()}
        style={{
          width: '100%',
          maxWidth: 400,
          background: color.surface,
          border: `1px solid ${color.border}`,
          borderRadius: radius.card,
          boxShadow: shadow.card,
          padding: 20,
        }}
      >
        <h2 style={{ margin: 0, fontSize: 15, fontWeight: 800, color: color.heading }}>{title}</h2>
        <p style={{ margin: '8px 0 18px', fontSize: 12.5, color: color.muted, fontWeight: 500, lineHeight: 1.6 }}>
          {message}
        </p>
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <Button type="button" variant="secondary" onClick={onCancel} disabled={busy}>
            Cancel
          </Button>
          <Button type="button" onClick={onConfirm} disabled={busy}>
            {busy ? 'Working...' : confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  );
}
