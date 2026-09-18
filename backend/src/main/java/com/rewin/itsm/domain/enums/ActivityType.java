package com.rewin.itsm.domain.enums;

/**
 * What an entry in an incident's activity history records.
 *
 * <p>The twelve specified actions, plus three the lifecycle actually produces:
 * INCIDENT_UPDATED for a field edit such as a category change, INCIDENT_REOPENED
 * for a resolved incident sent back to IN_PROGRESS, and SLA_AT_RISK for the
 * moment an incident enters the last quarter of its window - the warning that
 * makes the breach that follows unsurprising.
 */
public enum ActivityType {
    INCIDENT_CREATED,
    INCIDENT_ASSIGNED,
    INCIDENT_REASSIGNED,
    TEAM_CHANGED,
    AGENT_CHANGED,
    STATUS_CHANGED,
    PRIORITY_CHANGED,
    INCIDENT_UPDATED,
    COMMENT_ADDED,
    INCIDENT_RESOLVED,
    INCIDENT_CLOSED,
    INCIDENT_REOPENED,
    SLA_AT_RISK,
    SLA_BREACHED,
    SLA_COMPLETED
}
