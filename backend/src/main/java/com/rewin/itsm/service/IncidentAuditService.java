package com.rewin.itsm.service;

import com.rewin.itsm.domain.enums.ActivityType;
import com.rewin.itsm.domain.enums.IncidentStatus;
import com.rewin.itsm.domain.enums.Priority;
import com.rewin.itsm.domain.incident.Incident;
import com.rewin.itsm.domain.incident.IncidentActivity;
import com.rewin.itsm.domain.team.Team;
import com.rewin.itsm.domain.user.User;
import com.rewin.itsm.repository.IncidentActivityRepository;
import org.springframework.stereotype.Service;

/**
 * Writes the audit trail.
 *
 * <p>Every description is built here from the actual user, team and incident
 * involved - no name, team or status is ever hardcoded, and nothing is assembled
 * in the UI, so the sentence stored is the sentence everyone sees.
 *
 * <p>This service only ever appends. It has no update or delete method, and
 * {@link IncidentActivity} has no setters, so the trail cannot be rewritten.
 */
@Service
public class IncidentAuditService {

    /** Used when the system itself acted, rather than a person. */
    private static final String SYSTEM_ACTOR = "System";

    private final IncidentActivityRepository activityRepository;

    public IncidentAuditService(IncidentActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    public void incidentCreated(Incident incident, User actor) {
        record(incident, actor, ActivityType.INCIDENT_CREATED,
            "Incident created by " + nameOf(actor), null, incident.getStatus().name());
    }

    /**
     * An agent was put on the incident. The first one is an assignment; any
     * later one is a reassignment, which is a different fact about how the
     * incident has been handled.
     */
    public void agentAssigned(Incident incident, User actor, User previousAgent, User newAgent) {
        boolean reassignment = previousAgent != null && !previousAgent.getId().equals(newAgent.getId());
        if (reassignment) {
            record(incident, actor, ActivityType.INCIDENT_REASSIGNED,
                "Incident reassigned from " + nameOf(previousAgent) + " to " + nameOf(newAgent)
                    + " by " + nameOf(actor),
                nameOf(previousAgent), nameOf(newAgent));
            record(incident, actor, ActivityType.AGENT_CHANGED,
                "Assigned agent changed from " + nameOf(previousAgent) + " to " + nameOf(newAgent),
                nameOf(previousAgent), nameOf(newAgent));
            return;
        }
        record(incident, actor, ActivityType.INCIDENT_ASSIGNED,
            "Incident assigned to " + nameOf(newAgent) + " by " + nameOf(actor),
            null, nameOf(newAgent));
    }

    public void teamAssigned(Incident incident, User actor, Team previousTeam, Team newTeam) {
        if (previousTeam == null) {
            record(incident, actor, ActivityType.INCIDENT_ASSIGNED,
                "Incident assigned to " + newTeam.getName() + " by " + nameOf(actor),
                null, newTeam.getName());
            return;
        }
        record(incident, actor, ActivityType.TEAM_CHANGED,
            "Team changed from " + previousTeam.getName() + " to " + newTeam.getName()
                + " by " + nameOf(actor),
            previousTeam.getName(), newTeam.getName());
    }

    public void statusChanged(Incident incident, User actor, IncidentStatus from, IncidentStatus to, String note) {
        String description = "Status changed from " + from.name() + " to " + to.name() + " by " + nameOf(actor);
        record(incident, actor, ActivityType.STATUS_CHANGED, withNote(description, note), from.name(), to.name());
    }

    public void statusChangedAutomatically(Incident incident, User actor, IncidentStatus from, IncidentStatus to) {
        record(incident, actor, ActivityType.STATUS_CHANGED,
            "Status changed from " + from.name() + " to " + to.name() + " automatically on assignment",
            from.name(), to.name());
    }

    public void priorityChanged(Incident incident, User actor, Priority from, Priority to) {
        record(incident, actor, ActivityType.PRIORITY_CHANGED,
            "Priority changed from " + from.name() + " to " + to.name() + " by " + nameOf(actor),
            from.name(), to.name());
    }

    public void fieldUpdated(Incident incident, User actor, String field, String from, String to) {
        record(incident, actor, ActivityType.INCIDENT_UPDATED,
            field + " changed from " + from + " to " + to + " by " + nameOf(actor), from, to);
    }

    public void commentAdded(Incident incident, User author, boolean internal) {
        record(incident, author, ActivityType.COMMENT_ADDED,
            (internal ? "Internal note added by " : "Comment added by ") + nameOf(author), null, null);
    }

    public void incidentResolved(Incident incident, User actor, IncidentStatus from, String summary) {
        record(incident, actor, ActivityType.INCIDENT_RESOLVED,
            withNote("Incident resolved by " + nameOf(actor), summary), from.name(), IncidentStatus.RESOLVED.name());
    }

    public void incidentClosed(Incident incident, User actor, String note) {
        record(incident, actor, ActivityType.INCIDENT_CLOSED,
            withNote("Incident closed by " + nameOf(actor), note),
            IncidentStatus.RESOLVED.name(), IncidentStatus.CLOSED.name());
    }

    public void incidentReopened(Incident incident, User actor, String note) {
        record(incident, actor, ActivityType.INCIDENT_REOPENED,
            withNote("Incident reopened by " + nameOf(actor), note),
            IncidentStatus.RESOLVED.name(), IncidentStatus.IN_PROGRESS.name());
    }

    /**
     * Recorded with no actor: nobody performs a breach, it is noticed - by the
     * sweep, or by whoever opens the incident first.
     *
     * <p>{@code stage} names which clock ran out, because "SLA breached" alone
     * does not tell an agent whether nobody answered or nobody finished.
     */
    public void slaStageBreached(Incident incident, String stage) {
        record(incident, null, ActivityType.SLA_BREACHED,
            stage + " SLA breached for incident " + incident.getReference(),
            null, stage.toUpperCase(java.util.Locale.ROOT));
    }

    /** The warning that makes the breach that follows unsurprising. */
    public void slaAtRisk(Incident incident) {
        record(incident, null, ActivityType.SLA_AT_RISK,
            "SLA at risk for incident " + incident.getReference(), null, "AT_RISK");
    }

    public void slaCompleted(Incident incident) {
        record(incident, null, ActivityType.SLA_COMPLETED,
            "SLA met for incident " + incident.getReference(), null, "COMPLETED");
    }

    private void record(Incident incident, User actor, ActivityType action, String description,
                        String oldValue, String newValue) {
        activityRepository.save(new IncidentActivity(incident, actor, action, truncate(description),
            truncate(oldValue, 160), truncate(newValue, 160)));
    }

    private String withNote(String description, String note) {
        return note == null || note.isBlank() ? description : description + ": " + note.trim();
    }

    private String nameOf(User user) {
        return user != null ? user.getName() : SYSTEM_ACTOR;
    }

    private String truncate(String value) {
        return truncate(value, 500);
    }

    /** The description column is bounded; a long note must not fail the write. */
    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
