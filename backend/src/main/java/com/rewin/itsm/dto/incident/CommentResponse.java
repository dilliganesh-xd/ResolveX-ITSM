package com.rewin.itsm.dto.incident;

import com.rewin.itsm.domain.enums.RoleName;
import com.rewin.itsm.domain.incident.IncidentComment;
import java.time.Instant;
import java.util.UUID;

/**
 * A comment as the API exposes it. The author is reduced to id, name and role -
 * their account, and certainly their password hash, never travels with it.
 */
public record CommentResponse(
    UUID id,
    UUID incidentId,
    UUID authorId,
    String authorName,
    RoleName authorRole,
    String comment,
    boolean internal,
    Instant createdAt,
    Instant updatedAt) {

    public static CommentResponse from(IncidentComment source) {
        return new CommentResponse(
            source.getId(),
            source.getIncident().getId(),
            source.getAuthor().getId(),
            source.getAuthor().getName(),
            source.getAuthor().getRole(),
            source.getComment(),
            source.isInternal(),
            source.getCreatedAt(),
            source.getUpdatedAt());
    }
}
