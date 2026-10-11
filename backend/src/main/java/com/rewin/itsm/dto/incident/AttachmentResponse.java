package com.rewin.itsm.dto.incident;

import com.rewin.itsm.domain.incident.IncidentAttachment;
import java.time.Instant;
import java.util.UUID;

/**
 * An attachment as the API exposes it. The storage key is internal and is not
 * included: files are only ever reached through the authorized download
 * endpoint, never by a bucket URL.
 */
public record AttachmentResponse(
    UUID id,
    UUID incidentId,
    String fileName,
    String contentType,
    long sizeBytes,
    UUID uploadedById,
    String uploadedByName,
    Instant createdAt) {

    public static AttachmentResponse from(IncidentAttachment source) {
        return new AttachmentResponse(
            source.getId(),
            source.getIncident().getId(),
            source.getOriginalFilename(),
            source.getContentType(),
            source.getSizeBytes(),
            source.getUploadedBy().getId(),
            source.getUploadedBy().getName(),
            source.getCreatedAt());
    }
}
