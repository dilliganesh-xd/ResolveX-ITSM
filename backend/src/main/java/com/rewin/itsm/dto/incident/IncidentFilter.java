package com.rewin.itsm.dto.incident;

import com.rewin.itsm.domain.enums.IncidentStatus;
import com.rewin.itsm.domain.enums.Priority;
import com.rewin.itsm.domain.enums.SlaStatus;
import java.util.UUID;

/**
 * Queue filters. Every field is optional; a null means "do not narrow by this".
 * Bound from query parameters by Spring.
 */
public record IncidentFilter(IncidentStatus status, Priority priority, UUID teamId, SlaStatus slaStatus,
                             String search) {

    public boolean hasSearch() {
        return search != null && !search.isBlank();
    }
}
