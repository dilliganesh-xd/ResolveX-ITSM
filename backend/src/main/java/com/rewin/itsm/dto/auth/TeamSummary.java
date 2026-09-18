package com.rewin.itsm.dto.auth;

import com.rewin.itsm.domain.team.Team;
import java.util.UUID;

public record TeamSummary(UUID id, String name) {

    public static TeamSummary from(Team team) {
        return team == null ? null : new TeamSummary(team.getId(), team.getName());
    }
}
