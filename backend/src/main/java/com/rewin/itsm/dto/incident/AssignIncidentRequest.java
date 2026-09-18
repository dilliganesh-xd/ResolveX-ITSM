package com.rewin.itsm.dto.incident;

import java.util.UUID;

/** Either field may be null: an incident can be routed to a team before an agent is picked. */
public record AssignIncidentRequest(UUID agentId, UUID teamId) {
}
