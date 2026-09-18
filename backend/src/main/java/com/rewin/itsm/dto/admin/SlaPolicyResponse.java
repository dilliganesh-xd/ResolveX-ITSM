package com.rewin.itsm.dto.admin;

import com.rewin.itsm.domain.enums.Priority;
import com.rewin.itsm.domain.sla.SlaPolicy;
import java.time.Instant;
import java.util.UUID;

public record SlaPolicyResponse(
    UUID id, String name, Priority priority,
    int responseTimeMinutes, int resolutionTimeMinutes, boolean active,
    Instant createdAt, Instant updatedAt) {

    public static SlaPolicyResponse from(SlaPolicy policy) {
        return new SlaPolicyResponse(policy.getId(), policy.getName(), policy.getPriority(),
            policy.getResponseTimeMinutes(), policy.getResolutionTimeMinutes(), policy.isActive(),
            policy.getCreatedAt(), policy.getUpdatedAt());
    }
}
