package com.rewin.itsm.dto.admin;

import com.rewin.itsm.domain.team.Team;
import java.time.Instant;
import java.util.UUID;

public record TeamResponse(
    UUID id,
    String name,
    String description,
    boolean active,
    long memberCount,
    Instant createdAt,
    Instant updatedAt) {

    public static TeamResponse from(Team team, long memberCount) {
        return new TeamResponse(team.getId(), team.getName(), team.getDescription(),
            team.isActive(), memberCount, team.getCreatedAt(), team.getUpdatedAt());
    }
}
