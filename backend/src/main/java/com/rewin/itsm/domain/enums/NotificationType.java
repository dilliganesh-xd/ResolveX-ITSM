package com.rewin.itsm.domain.enums;

/** What a notification is about. */
public enum NotificationType {
    INCIDENT_CREATED,
    INCIDENT_ASSIGNED,
    INCIDENT_UPDATED,
    COMMENT_ADDED,
    SLA_AT_RISK,
    SLA_BREACHED,
    INCIDENT_RESOLVED,
    INCIDENT_CLOSED
}
