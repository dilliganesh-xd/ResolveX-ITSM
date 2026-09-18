package com.rewin.itsm.domain.enums;

/** The incident lifecycle. Transitions are enforced in the service layer. */
public enum IncidentStatus {
    NEW,
    ASSIGNED,
    IN_PROGRESS,
    RESOLVED,
    CLOSED
}
