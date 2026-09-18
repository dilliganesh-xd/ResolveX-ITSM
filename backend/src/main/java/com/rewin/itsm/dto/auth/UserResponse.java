package com.rewin.itsm.dto.auth;

import com.rewin.itsm.domain.enums.RoleName;
import com.rewin.itsm.domain.user.User;
import java.time.Instant;
import java.util.UUID;

/**
 * The public shape of a user. There is deliberately no password field on this
 * record, so a hash cannot be serialised by accident.
 */
public record UserResponse(
    UUID id,
    String name,
    String email,
    RoleName role,
    TeamSummary team,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getRole(),
            TeamSummary.from(user.getTeam()),
            user.isActive(),
            user.getCreatedAt(),
            user.getUpdatedAt());
    }
}
