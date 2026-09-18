package com.rewin.itsm.domain.sla;

import com.rewin.itsm.common.BaseEntity;
import com.rewin.itsm.domain.enums.Priority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * V1 resolves an incident's SLA by priority alone. The unique constraint on
 * (priority, name) keeps a priority from silently acquiring two identically
 * named policies; selecting the single active one per priority is the service
 * layer's job in Phase 3.
 */
@Entity
@Table(
    name = "sla_policies",
    uniqueConstraints = @UniqueConstraint(name = "uk_sla_policy_priority_name", columnNames = {"priority", "name"}),
    indexes = @Index(name = "ix_sla_policies_priority_active", columnList = "priority, active"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SlaPolicy extends BaseEntity {

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 16)
    private Priority priority;

    /** Time allowed to first respond, in minutes. */
    @Column(name = "response_time_minutes", nullable = false)
    private int responseTimeMinutes;

    /** Time allowed to resolve, in minutes. Drives the incident's SLA deadline. */
    @Column(name = "resolution_time_minutes", nullable = false)
    private int resolutionTimeMinutes;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public SlaPolicy(String name, Priority priority, int responseTimeMinutes, int resolutionTimeMinutes) {
        this.name = name;
        this.priority = priority;
        this.responseTimeMinutes = responseTimeMinutes;
        this.resolutionTimeMinutes = resolutionTimeMinutes;
    }
}
