package com.rewin.itsm.dto.incident;

import com.rewin.itsm.domain.enums.ActivityType;
import com.rewin.itsm.domain.enums.RoleName;
import com.rewin.itsm.domain.incident.IncidentActivity;
import java.time.Instant;
import java.util.UUID;

/**
 * One audit line. {@code description} is generated server-side when the action
 * happens, so every client renders the same sentence rather than reassembling
 * one from the parts.
 */
public record ActivityResponse(
    UUID id,
    UUID incidentId,
    ActivityType action,
    String description,
    String oldValue,
    String newValue,
    UUID performedById,
    String performedByName,
    RoleName performedByRole,
    Instant createdAt) {

    public static ActivityResponse from(IncidentActivity source) {
        boolean hasActor = source.getPerformedBy() != null;
        return new ActivityResponse(
            source.getId(),
            source.getIncident().getId(),
            source.getAction(),
            source.getDescription(),
            source.getOldValue(),
            source.getNewValue(),
            hasActor ? source.getPerformedBy().getId() : null,
            hasActor ? source.getPerformedBy().getName() : "System",
            hasActor ? source.getPerformedBy().getRole() : null,
            source.getCreatedAt());
    }
}
