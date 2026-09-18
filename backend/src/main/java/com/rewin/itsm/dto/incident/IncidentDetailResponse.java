package com.rewin.itsm.dto.incident;

import com.rewin.itsm.domain.enums.IncidentStatus;
import com.rewin.itsm.domain.enums.Priority;
import com.rewin.itsm.domain.enums.SlaStatus;
import com.rewin.itsm.domain.incident.Incident;
import com.rewin.itsm.dto.auth.TeamSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IncidentDetailResponse(
    UUID id,
    String reference,
    String title,
    String description,
    String category,
    String subcategory,
    Priority priority,
    IncidentStatus status,
    UUID reporterId,
    String reporterName,
    UUID assignedAgentId,
    String assignedAgentName,
    TeamSummary assignedTeam,
    String slaPolicyName,
    SlaStatus slaStatus,
    /**
     * Per-stage SLA verdicts, computed server-side by the same rules as the
     * overall status. The UI renders them; it does not work them out.
     */
    SlaStatus responseSlaStatus,
    SlaStatus resolutionSlaStatus,
    /** Everyone sees when resolution is due. */
    Instant resolutionDeadline,
    Instant resolutionCompletedAt,
    /** Support-side only: null in an employee's copy of this record. */
    Instant responseDeadline,
    Instant responseCompletedAt,
    Instant createdAt,
    Instant updatedAt,
    String resolutionSummary,
    Instant resolvedAt,
    Instant closedAt,
    List<CommentResponse> comments,
    List<ActivityResponse> activity) {

    /**
     * @param includeResponseStage false for an employee, who sees the SLA status
     *                             and the resolution deadline but not how the
     *                             support side measures its own first response.
     */
    public static IncidentDetailResponse from(
            Incident incident, List<CommentResponse> comments, List<ActivityResponse> activity,
            boolean includeResponseStage, SlaStatus responseStage, SlaStatus resolutionStage) {
        return new IncidentDetailResponse(
            incident.getId(),
            incident.getReference(),
            incident.getTitle(),
            incident.getDescription(),
            incident.getCategory(),
            incident.getSubcategory(),
            incident.getPriority(),
            incident.getStatus(),
            incident.getReporter().getId(),
            incident.getReporter().getName(),
            incident.getAssignedAgent() != null ? incident.getAssignedAgent().getId() : null,
            incident.getAssignedAgent() != null ? incident.getAssignedAgent().getName() : null,
            TeamSummary.from(incident.getAssignedTeam()),
            incident.getSlaPolicy() != null ? incident.getSlaPolicy().getName() : null,
            incident.getSlaStatus(),
            includeResponseStage ? responseStage : null,
            resolutionStage,
            incident.getResolutionDeadline(),
            incident.getResolutionCompletedAt(),
            includeResponseStage ? incident.getResponseDeadline() : null,
            includeResponseStage ? incident.getResponseCompletedAt() : null,
            incident.getCreatedAt(),
            incident.getUpdatedAt(),
            incident.getResolutionSummary(),
            incident.getResolvedAt(),
            incident.getClosedAt(),
            comments,
            activity);
    }
}
