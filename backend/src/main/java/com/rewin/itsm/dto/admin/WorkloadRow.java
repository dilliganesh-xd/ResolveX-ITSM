package com.rewin.itsm.dto.admin;

/**
 * One row of a workload table: a team or an agent, and what they are carrying.
 * {@code open} counts NEW and ASSIGNED, {@code resolved} counts RESOLVED and
 * CLOSED, so the three columns add up to the total.
 */
public record WorkloadRow(String name, long open, long inProgress, long resolved, long total) {
}
