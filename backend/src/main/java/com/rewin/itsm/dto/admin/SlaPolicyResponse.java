package com.rewin.itsm.dto.admin;

import com.rewin.itsm.domain.enums.Priority;
import com.rewin.itsm.domain.sla.SlaPolicy;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Serializable because the policy list is cached in Redis, whose default cache
 * serializer is JDK serialization.
 */
public record SlaPolicyResponse(
    UUID id, String name, Priority priority,
    int responseTimeMinutes, int resolutionTimeMinutes, boolean active,
    Instant createdAt, Instant updatedAt) implements Serializable {

    private static final long serialVersionUID = 1L;

    public static SlaPolicyResponse from(SlaPolicy policy) {
        return new SlaPolicyResponse(policy.getId(), policy.getName(), policy.getPriority(),
            policy.getResponseTimeMinutes(), policy.getResolutionTimeMinutes(), policy.isActive(),
            policy.getCreatedAt(), policy.getUpdatedAt());
    }
}
