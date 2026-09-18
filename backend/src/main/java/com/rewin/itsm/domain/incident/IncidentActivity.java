package com.rewin.itsm.domain.incident;

import com.rewin.itsm.common.BaseEntity;
import com.rewin.itsm.domain.enums.ActivityType;
import com.rewin.itsm.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One line of an incident's audit trail.
 *
 * <p><strong>Immutable by construction.</strong> There are no setters and every
 * column is {@code updatable = false}, so an activity cannot be edited once
 * written - not by a user, and not by a future service method that forgets the
 * rule. Nothing deletes activities either: there is no repository delete call
 * and no endpoint that exposes one.
 *
 * <p>{@code description} is the human-readable sentence, generated from the real
 * user and incident at the moment it happened. {@code oldValue} and
 * {@code newValue} keep the same change in structured form, so the trail can be
 * queried later without parsing prose.
 */
@Entity
@Table(name = "incident_activities", indexes = @Index(name = "ix_activities_incident", columnList = "incident_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IncidentActivity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false, updatable = false,
        foreignKey = @ForeignKey(name = "fk_activities_incident"))
    private Incident incident;

    /** Null when the system itself acted, for example noticing an SLA breach. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by_id", updatable = false,
        foreignKey = @ForeignKey(name = "fk_activities_performed_by"))
    private User performedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 32, updatable = false)
    private ActivityType action;

    @Column(name = "description", nullable = false, length = 500, updatable = false)
    private String description;

    @Column(name = "old_value", length = 160, updatable = false)
    private String oldValue;

    @Column(name = "new_value", length = 160, updatable = false)
    private String newValue;

    public IncidentActivity(Incident incident, User performedBy, ActivityType action, String description,
                            String oldValue, String newValue) {
        this.incident = incident;
        this.performedBy = performedBy;
        this.action = action;
        this.description = description;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }
}
