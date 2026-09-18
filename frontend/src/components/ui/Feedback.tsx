import { radius } from '@/lib/tokens';

/** The one error banner every admin screen uses. */
export function ErrorBanner({ message }: { message: string }) {
  return (
    <div
      role="alert"
      style={{
        marginBottom: 12,
        padding: '9px 11px',
        borderRadius: radius.control,
        background: '#FEF2F2',
        border: '1px solid #FECDD3',
        color: '#BE123C',
        fontSize: 12.5,
        fontWeight: 600,
      }}
    >
      {message}
    </div>
  );
}

/** And the one success banner. */
export function SuccessBanner({ message }: { message: string }) {
  return (
    <div
      role="status"
      style={{
        marginBottom: 12,
        padding: '9px 11px',
        borderRadius: radius.control,
        background: '#F0FDF4',
        border: '1px solid #D1E7DD',
        color: '#15803D',
        fontSize: 12.5,
        fontWeight: 600,
      }}
    >
      {message}
    </div>
  );
}
