package com.rewin.itsm.dto.admin;

import com.rewin.itsm.domain.enums.IncidentStatus;
import com.rewin.itsm.domain.enums.Priority;
import java.util.List;
import java.util.Map;

/** The admin reports payload for V1: counts only. */
public record ReportSummaryResponse(
    long totalIncidents,
    Map<IncidentStatus, Long> byStatus,
    Map<Priority, Long> byPriority,
    Map<String, Long> byTeam,
    long openIncidents,
    long onTrackIncidents,
    long atRiskIncidents,
    long breachedIncidents,
    long resolvedIncidents,
    long closedIncidents,
    long inProgressIncidents,
    long totalUsers,
    long activeUsers,
    long totalTeams,
    List<WorkloadRow> teamWorkload,
    List<WorkloadRow> agentWorkload) {
}
