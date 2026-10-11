import { api } from './client';
import type {
  Activity,
  Attachment,
  Comment,
  IncidentDetail,
  IncidentFilters,
  IncidentStatus,
  IncidentSummary,
  PageResponse,
  Priority,
} from '@/lib/types';

export interface CreateIncidentPayload {
  title: string;
  description: string;
  category: string;
  subcategory?: string;
  priority: Priority;
}

export interface UpdateIncidentPayload {
  title?: string;
  description?: string;
  category?: string;
  subcategory?: string;
  priority?: Priority;
}

/** Empty filter values are dropped so the API never receives `status=`. */
function toParams(filters?: IncidentFilters, page = 0, size = 20): Record<string, string | number> {
  const params: Record<string, string | number> = { page, size };
  if (!filters) {
    return params;
  }
  if (filters.status) {
    params.status = filters.status;
  }
  if (filters.priority) {
    params.priority = filters.priority;
  }
  if (filters.teamId) {
    params.teamId = filters.teamId;
  }
  if (filters.slaStatus) {
    params.slaStatus = filters.slaStatus;
  }
  if (filters.search && filters.search.trim()) {
    params.search = filters.search.trim();
  }
  return params;
}

/** Paged as of Phase 8: the caller gets the envelope, not a bare array. */
export async function listIncidents(
  filters?: IncidentFilters,
  page = 0,
  size = 20,
): Promise<PageResponse<IncidentSummary>> {
  const { data } = await api.get<PageResponse<IncidentSummary>>('/incidents', {
    params: toParams(filters, page, size),
  });
  return data;
}

export interface IncidentStats {
  total: number;
  open: number;
  inProgress: number;
  resolved: number;
  closed: number;
  onTrack: number;
  atRisk: number;
  breached: number;
  assignedToMe: number;
  unassigned: number;
  resolvedToday: number;
}

/** Dashboard counts, already scoped by the server to whoever is asking. */
export async function getIncidentStats(): Promise<IncidentStats> {
  const { data } = await api.get<IncidentStats>('/incidents/summary');
  return data;
}

export async function getIncident(id: string): Promise<IncidentDetail> {
  const { data } = await api.get<IncidentDetail>(`/incidents/${id}`);
  return data;
}

export async function createIncident(payload: CreateIncidentPayload): Promise<IncidentDetail> {
  const { data } = await api.post<IncidentDetail>('/incidents', payload);
  return data;
}

export async function updateIncident(id: string, payload: UpdateIncidentPayload): Promise<IncidentDetail> {
  const { data } = await api.put<IncidentDetail>(`/incidents/${id}`, payload);
  return data;
}

export async function assignIncident(
  id: string,
  payload: { agentId?: string; teamId?: string },
): Promise<IncidentDetail> {
  const { data } = await api.patch<IncidentDetail>(`/incidents/${id}/assign`, payload);
  return data;
}

export async function changeStatus(id: string, status: IncidentStatus, note?: string): Promise<IncidentDetail> {
  const { data } = await api.patch<IncidentDetail>(`/incidents/${id}/status`, { status, note });
  return data;
}

export async function resolveIncident(id: string, resolutionSummary: string): Promise<IncidentDetail> {
  const { data } = await api.patch<IncidentDetail>(`/incidents/${id}/resolve`, { resolutionSummary });
  return data;
}

export async function closeIncident(id: string, note?: string): Promise<IncidentDetail> {
  const { data } = await api.patch<IncidentDetail>(`/incidents/${id}/close`, { note });
  return data;
}

export async function addComment(id: string, comment: string, internal = false): Promise<Comment> {
  const { data } = await api.post<Comment>(`/incidents/${id}/comments`, { comment, internal });
  return data;
}

/** Oldest first: a conversation reads chronologically. */
export async function listComments(id: string): Promise<Comment[]> {
  const { data } = await api.get<PageResponse<Comment>>(`/incidents/${id}/comments`);
  return data.content;
}

/**
 * The audit trail. The API returns newest first; the timeline reads
 * chronologically, so it is reversed here rather than asking the server for an
 * order that only this one view wants.
 */
export async function listActivities(id: string): Promise<Activity[]> {
  const { data } = await api.get<PageResponse<Activity>>(`/incidents/${id}/activities`);
  return [...data.content].reverse();
}

// ------------------------------------------------------------ attachments

/** Uploads and downloads can be several megabytes, so they get longer than the default timeout. */
const FILE_TIMEOUT_MS = 120000;

export async function listAttachments(id: string): Promise<Attachment[]> {
  const { data } = await api.get<Attachment[]>(`/incidents/${id}/attachments`);
  return data;
}

/**
 * The Content-Type is set explicitly: the shared client defaults to JSON, and
 * Axios would otherwise serialise the FormData as a JSON object. The browser
 * adds the multipart boundary itself.
 */
export async function uploadAttachment(id: string, file: File): Promise<Attachment> {
  const form = new FormData();
  form.append('file', file);
  const { data } = await api.post<Attachment>(`/incidents/${id}/attachments`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: FILE_TIMEOUT_MS,
  });
  return data;
}

/**
 * Fetched through the API, with the user's token, rather than opened as a
 * plain link: the bucket is private and the backend checks access on every
 * download. The blob is then handed to the browser to save.
 */
export async function downloadAttachment(attachment: Attachment): Promise<void> {
  const { data } = await api.get<Blob>(
    `/incidents/${attachment.incidentId}/attachments/${attachment.id}/download`,
    { responseType: 'blob', timeout: FILE_TIMEOUT_MS },
  );
  const url = URL.createObjectURL(data);
  const link = document.createElement('a');
  link.href = url;
  link.download = attachment.fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

/** Admin only. */
export async function removeAttachment(attachment: Attachment): Promise<void> {
  await api.delete(`/incidents/${attachment.incidentId}/attachments/${attachment.id}`);
}
