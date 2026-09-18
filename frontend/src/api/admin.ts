import { api } from './client';
import type { PageResponse, Priority, RoleName, TeamSummary } from '@/lib/types';

export interface AdminUser {
  id: string;
  name: string;
  email: string;
  role: RoleName;
  team?: TeamSummary;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AdminUserDetail extends AdminUser {
  assignedIncidentCount: number;
  resolvedIncidentCount: number;
}

export interface CreateUserPayload {
  name: string;
  email: string;
  password: string;
  role: RoleName;
  teamId?: string;
}

export interface UpdateUserPayload {
  name: string;
  email: string;
  role: RoleName;
  teamId?: string;
  /** Null cannot mean both "unchanged" and "none", so removal is explicit. */
  clearTeam?: boolean;
  active: boolean;
}

export interface UserFilters {
  search?: string;
  role?: RoleName | '';
  teamId?: string;
  active?: 'true' | 'false' | '';
}

export interface Team {
  id: string;
  name: string;
  description?: string;
  active: boolean;
  memberCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface TeamPayload {
  name: string;
  description?: string;
  active?: boolean;
}

export interface TeamMember {
  id: string;
  name: string;
  email: string;
  role: RoleName;
  active: boolean;
}

export interface SlaPolicy {
  id: string;
  name: string;
  priority: Priority;
  responseTimeMinutes: number;
  resolutionTimeMinutes: number;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface SlaPolicyPayload {
  name: string;
  priority: Priority;
  responseTimeMinutes: number;
  resolutionTimeMinutes: number;
  active: boolean;
}

export interface WorkloadRow {
  name: string;
  open: number;
  inProgress: number;
  resolved: number;
  total: number;
}

export interface ReportSummary {
  totalIncidents: number;
  byStatus: Record<string, number>;
  byPriority: Record<string, number>;
  byTeam: Record<string, number>;
  openIncidents: number;
  atRiskIncidents: number;
  breachedIncidents: number;
  resolvedIncidents: number;
  closedIncidents: number;
  inProgressIncidents: number;
  totalUsers: number;
  activeUsers: number;
  totalTeams: number;
  onTrackIncidents: number;
  teamWorkload: WorkloadRow[];
  agentWorkload: WorkloadRow[];
}

export interface AdminAuditEntry {
  id: string;
  performedByName: string;
  targetType: string;
  targetName: string;
  description: string;
  createdAt: string;
}

/** Empty filter values are dropped so the API never receives `role=`. */
function toParams(filters?: UserFilters, page = 0, size = 50): Record<string, string | number> {
  const params: Record<string, string | number> = { page, size };
  if (filters?.search?.trim()) {
    params.search = filters.search.trim();
  }
  if (filters?.role) {
    params.role = filters.role;
  }
  if (filters?.teamId) {
    params.teamId = filters.teamId;
  }
  if (filters?.active) {
    params.active = filters.active;
  }
  return params;
}

export async function listUsers(
  filters?: UserFilters,
  page = 0,
  size = 50,
): Promise<PageResponse<AdminUser>> {
  const { data } = await api.get<PageResponse<AdminUser>>('/users', { params: toParams(filters, page, size) });
  return data;
}

export async function getUser(id: string): Promise<AdminUserDetail> {
  const { data } = await api.get<AdminUserDetail>(`/users/${id}`);
  return data;
}

export async function createUser(payload: CreateUserPayload): Promise<AdminUser> {
  const { data } = await api.post<AdminUser>('/users', payload);
  return data;
}

export async function updateUser(id: string, payload: UpdateUserPayload): Promise<AdminUser> {
  const { data } = await api.put<AdminUser>(`/users/${id}`, payload);
  return data;
}

export async function setUserActive(id: string, active: boolean): Promise<AdminUser> {
  const { data } = await api.patch<AdminUser>(`/users/${id}/active`, null, { params: { active } });
  return data;
}

export async function listTeams(): Promise<Team[]> {
  const { data } = await api.get<Team[]>('/teams');
  return data;
}

export async function listTeamMembers(id: string): Promise<TeamMember[]> {
  const { data } = await api.get<TeamMember[]>(`/teams/${id}/members`);
  return data;
}

export async function createTeam(payload: TeamPayload): Promise<Team> {
  const { data } = await api.post<Team>('/teams', payload);
  return data;
}

export async function updateTeam(id: string, payload: Required<TeamPayload>): Promise<Team> {
  const { data } = await api.put<Team>(`/teams/${id}`, payload);
  return data;
}

export async function setTeamActive(id: string, active: boolean): Promise<Team> {
  const { data } = await api.patch<Team>(`/teams/${id}/active`, null, { params: { active } });
  return data;
}

export async function listSlaPolicies(): Promise<SlaPolicy[]> {
  const { data } = await api.get<SlaPolicy[]>('/sla-policies');
  return data;
}

export async function createSlaPolicy(payload: SlaPolicyPayload): Promise<SlaPolicy> {
  const { data } = await api.post<SlaPolicy>('/sla-policies', payload);
  return data;
}

export async function updateSlaPolicy(id: string, payload: SlaPolicyPayload): Promise<SlaPolicy> {
  const { data } = await api.put<SlaPolicy>(`/sla-policies/${id}`, payload);
  return data;
}

/** Activating a policy stands down the other policies for that priority. */
export async function setSlaPolicyActive(id: string, active: boolean): Promise<SlaPolicy> {
  const { data } = await api.patch<SlaPolicy>(`/sla-policies/${id}/active`, null, { params: { active } });
  return data;
}

/** Admin-assisted reset. The new password is sent once and never returned. */
export async function resetUserPassword(id: string, password: string): Promise<void> {
  await api.post(`/users/${id}/reset-password`, { password });
}

export async function getReport(): Promise<ReportSummary> {
  const { data } = await api.get<ReportSummary>('/reports');
  return data;
}

export async function listAgents(): Promise<AdminUser[]> {
  const { data } = await api.get<AdminUser[]>('/agents');
  return data;
}

export async function listAdminAudit(): Promise<AdminAuditEntry[]> {
  const { data } = await api.get<PageResponse<AdminAuditEntry>>('/admin/audit');
  return data.content;
}
