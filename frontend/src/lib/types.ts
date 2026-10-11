export type RoleName = 'EMPLOYEE' | 'SUPPORT_AGENT' | 'ADMIN';

export type IncidentStatus = 'NEW' | 'ASSIGNED' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';

export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export type SlaStatus = 'ON_TRACK' | 'AT_RISK' | 'BREACHED' | 'COMPLETED';

export interface TeamSummary {
  id: string;
  name: string;
}

export interface AuthUser {
  userId: string;
  name: string;
  email: string;
  role: RoleName;
  /** Absent for users with no team - the API omits null fields. */
  team?: TeamSummary;
}

export interface LoginResponse extends AuthUser {
  token: string;
  tokenType: string;
  expiresInSeconds: number;
}

export interface IncidentSummary {
  id: string;
  reference: string;
  title: string;
  category: string;
  priority: Priority;
  status: IncidentStatus;
  reporterName: string;
  assignedAgentName?: string;
  assignedTeamName?: string;
  responseDeadline?: string;
  resolutionDeadline?: string;
  slaStatus?: SlaStatus;
  /** Per-stage verdicts, decided server-side. */
  responseSlaStatus?: SlaStatus;
  resolutionSlaStatus?: SlaStatus;
  createdAt: string;
  updatedAt: string;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  fieldErrors?: Record<string, string>;
}

export interface Comment {
  id: string;
  incidentId: string;
  authorId: string;
  authorName: string;
  authorRole: RoleName;
  comment: string;
  internal: boolean;
  createdAt: string;
  updatedAt: string;
}

/** A file attached to an incident. The file itself is only reachable through the download endpoint. */
export interface Attachment {
  id: string;
  incidentId: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  uploadedById: string;
  uploadedByName: string;
  createdAt: string;
}

export type ActivityType =
  | 'INCIDENT_CREATED'
  | 'INCIDENT_ASSIGNED'
  | 'INCIDENT_REASSIGNED'
  | 'TEAM_CHANGED'
  | 'AGENT_CHANGED'
  | 'STATUS_CHANGED'
  | 'PRIORITY_CHANGED'
  | 'INCIDENT_UPDATED'
  | 'COMMENT_ADDED'
  | 'INCIDENT_RESOLVED'
  | 'INCIDENT_CLOSED'
  | 'INCIDENT_REOPENED'
  | 'SLA_AT_RISK'
  | 'SLA_BREACHED'
  | 'SLA_COMPLETED'
  | 'ATTACHMENT_ADDED'
  | 'ATTACHMENT_REMOVED';

export interface Activity {
  id: string;
  incidentId: string;
  action: ActivityType;
  /** Generated server-side; rendered as-is rather than reassembled here. */
  description: string;
  oldValue?: string;
  newValue?: string;
  performedById?: string;
  performedByName: string;
  performedByRole?: RoleName;
  createdAt: string;
}

/** The envelope every collection endpoint returns. */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface IncidentDetail {
  id: string;
  reference: string;
  title: string;
  description: string;
  category: string;
  subcategory?: string;
  priority: Priority;
  status: IncidentStatus;
  reporterId: string;
  reporterName: string;
  assignedAgentId?: string;
  assignedAgentName?: string;
  assignedTeam?: TeamSummary;
  slaPolicyName?: string;
  slaStatus?: SlaStatus;
  responseSlaStatus?: SlaStatus;
  resolutionSlaStatus?: SlaStatus;
  /** Everyone sees this. */
  resolutionDeadline?: string;
  resolutionCompletedAt?: string;
  /** Support-side only: the API omits these for an employee. */
  responseDeadline?: string;
  responseCompletedAt?: string;
  createdAt: string;
  updatedAt: string;
  resolutionSummary?: string;
  resolvedAt?: string;
  closedAt?: string;
  comments: Comment[];
  activity: Activity[];
}

export interface IncidentFilters {
  status?: IncidentStatus | '';
  priority?: Priority | '';
  teamId?: string;
  slaStatus?: SlaStatus | '';
  search?: string;
}

export type NotificationType =
  | 'INCIDENT_CREATED'
  | 'INCIDENT_ASSIGNED'
  | 'INCIDENT_UPDATED'
  | 'COMMENT_ADDED'
  | 'SLA_AT_RISK'
  | 'SLA_BREACHED'
  | 'INCIDENT_RESOLVED'
  | 'INCIDENT_CLOSED';

export interface AppNotification {
  id: string;
  type: NotificationType;
  title: string;
  message: string;
  read: boolean;
  relatedIncidentId?: string;
  relatedIncidentReference?: string;
  createdAt: string;
}
