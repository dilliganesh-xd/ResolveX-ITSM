package com.rewin.itsm.dto.admin;

import com.rewin.itsm.domain.enums.RoleName;
import com.rewin.itsm.domain.user.User;
import com.rewin.itsm.dto.auth.TeamSummary;
import java.time.Instant;
import java.util.UUID;

/**
 * A user with the two workload figures an admin actually asks for. Like every
 * user-shaped response, it has no password field at all, so a hash cannot be
 * serialised by accident.
 */
public record UserDetailResponse(
    UUID id,
    String name,
    String email,
    RoleName role,
    TeamSummary team,
    boolean active,
    Instant createdAt,
    Instant updatedAt,
    long assignedIncidentCount,
    long resolvedIncidentCount) {

    public static UserDetailResponse from(User user, long assigned, long resolved) {
        return new UserDetailResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getRole(),
            TeamSummary.from(user.getTeam()),
            user.isActive(),
            user.getCreatedAt(),
            user.getUpdatedAt(),
            assigned,
            resolved);
    }
}
