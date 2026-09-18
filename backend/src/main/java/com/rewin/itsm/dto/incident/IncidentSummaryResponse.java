package com.rewin.itsm.dto.incident;

import com.rewin.itsm.domain.enums.IncidentStatus;
import com.rewin.itsm.domain.enums.Priority;
import com.rewin.itsm.domain.enums.SlaStatus;
import com.rewin.itsm.domain.incident.Incident;
import java.time.Instant;
import java.util.UUID;

/** List-row shape. Deliberately smaller than the detail response. */
public record IncidentSummaryResponse(
    UUID id,
    String reference,
    String title,
    String category,
    Priority priority,
    IncidentStatus status,
    String reporterName,
    String assignedAgentName,
    String assignedTeamName,
    Instant responseDeadline,
    Instant resolutionDeadline,
    SlaStatus slaStatus,
    /** Per-stage, so a row can say which clock is in trouble rather than only that one is. */
    SlaStatus responseSlaStatus,
    SlaStatus resolutionSlaStatus,
    Instant createdAt,
    Instant updatedAt) {

    public static IncidentSummaryResponse from(Incident incident, SlaStatus responseStage,
                                               SlaStatus resolutionStage) {
        return new IncidentSummaryResponse(
            incident.getId(),
            incident.getReference(),
            incident.getTitle(),
            incident.getCategory(),
            incident.getPriority(),
            incident.getStatus(),
            incident.getReporter().getName(),
            incident.getAssignedAgent() != null ? incident.getAssignedAgent().getName() : null,
            incident.getAssignedTeam() != null ? incident.getAssignedTeam().getName() : null,
            incident.getResponseDeadline(),
            incident.getResolutionDeadline(),
            incident.getSlaStatus(),
            responseStage,
            resolutionStage,
            incident.getCreatedAt(),
            incident.getUpdatedAt());
    }
}
