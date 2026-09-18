package com.rewin.itsm.dto.notification;

import com.rewin.itsm.domain.enums.NotificationType;
import com.rewin.itsm.domain.notification.Notification;
import java.time.Instant;
import java.util.UUID;

/** A notification as the API exposes it. The recipient is implied by the token. */
public record NotificationResponse(
    UUID id,
    NotificationType type,
    String title,
    String message,
    boolean read,
    UUID relatedIncidentId,
    String relatedIncidentReference,
    Instant createdAt) {

    public static NotificationResponse from(Notification source) {
        boolean hasIncident = source.getRelatedIncident() != null;
        return new NotificationResponse(
            source.getId(),
            source.getType(),
            source.getTitle(),
            source.getMessage(),
            source.isRead(),
            hasIncident ? source.getRelatedIncident().getId() : null,
            hasIncident ? source.getRelatedIncident().getReference() : null,
            source.getCreatedAt());
    }
}
