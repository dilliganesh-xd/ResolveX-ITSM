package com.rewin.itsm.dto.admin;

import com.rewin.itsm.domain.audit.AdminAuditEntry;
import java.time.Instant;
import java.util.UUID;

public record AdminAuditResponse(
    UUID id,
    String performedByName,
    String targetType,
    String targetName,
    String description,
    Instant createdAt) {

    public static AdminAuditResponse from(AdminAuditEntry entry) {
        return new AdminAuditResponse(
            entry.getId(),
            entry.getPerformedBy() != null ? entry.getPerformedBy().getName() : "System",
            entry.getTargetType(),
            entry.getTargetName(),
            entry.getDescription(),
            entry.getCreatedAt());
    }
}
