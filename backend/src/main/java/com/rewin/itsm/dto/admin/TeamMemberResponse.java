package com.rewin.itsm.dto.admin;

import com.rewin.itsm.domain.enums.RoleName;
import com.rewin.itsm.domain.user.User;
import java.util.UUID;

public record TeamMemberResponse(UUID id, String name, String email, RoleName role, boolean active) {

    public static TeamMemberResponse from(User user) {
        return new TeamMemberResponse(user.getId(), user.getName(), user.getEmail(),
            user.getRole(), user.isActive());
    }
}
