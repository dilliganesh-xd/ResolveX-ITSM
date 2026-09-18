package com.rewin.itsm.dto.incident;

/**
 * Counts for a dashboard, already scoped to whoever asked.
 *
 * <p>An employee gets their own incidents, an agent gets their queue, an admin
 * gets everything - the same scoping the list endpoint applies, so the cards and
 * the table below them can never disagree.
 *
 * <p>{@code assignedToMe}, {@code unassigned} and {@code resolvedToday} are the
 * figures an agent works from; they are still computed for the other roles
 * because the scope makes them harmless there.
 */
public record IncidentSummaryStats(
    long total,
    long open,
    long inProgress,
    long resolved,
    long closed,
    long onTrack,
    long atRisk,
    long breached,
    long assignedToMe,
    long unassigned,
    long resolvedToday) {
}
