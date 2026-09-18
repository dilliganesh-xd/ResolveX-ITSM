package com.rewin.itsm.dto.admin;

import com.rewin.itsm.domain.enums.RoleName;
import java.util.UUID;

/**
 * Admin user-list filters. Every field is optional; null means "do not narrow by
 * this". Bound from query parameters by Spring.
 */
public record UserFilter(String search, RoleName role, UUID teamId, Boolean active) {

    public boolean hasSearch() {
        return search != null && !search.isBlank();
    }
}
