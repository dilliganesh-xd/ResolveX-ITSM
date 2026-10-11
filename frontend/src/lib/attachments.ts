/**
 * The attachment rules as the screens apply them, before a file is uploaded.
 *
 * They mirror the server's allow-list and size limit so an obviously wrong file
 * is caught immediately. The server still checks every file, including that its
 * contents match its extension - this is only fast feedback.
 */

/** For the file input's `accept` attribute and the extension check. */
export const ATTACHMENT_ACCEPT = '.png,.jpg,.jpeg,.gif,.webp,.pdf,.txt,.log,.csv,.docx,.xlsx';

export const ATTACHMENT_MAX_BYTES = 10 * 1024 * 1024;

export const ATTACHMENT_TYPES_LABEL = 'PNG, JPG, GIF, WEBP, PDF, TXT, LOG, CSV, DOCX and XLSX';

/** Why this file cannot be attached, or null when it can. */
export function attachmentProblem(file: File): string | null {
  const extension = file.name.includes('.') ? `.${file.name.split('.').pop()?.toLowerCase()}` : '';
  if (!ATTACHMENT_ACCEPT.split(',').includes(extension)) {
    return `${file.name}: only ${ATTACHMENT_TYPES_LABEL} files can be attached.`;
  }
  if (file.size === 0) {
    return `${file.name} is empty.`;
  }
  if (file.size > ATTACHMENT_MAX_BYTES) {
    return `${file.name} is larger than 10 MB.`;
  }
  return null;
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${Math.round(bytes / 1024)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
