package com.rewin.itsm.domain.audit;

import com.rewin.itsm.common.BaseEntity;
import com.rewin.itsm.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * An administrative action: a user created, a role changed, a policy activated.
 *
 * <p>Deliberately separate from IncidentActivity, which belongs to one incident
 * and is read as that incident's story. These actions belong to no incident, and
 * forcing them into that table would mean every incident timeline query had to
 * exclude them.
 *
 * <p>Minimal on purpose: who did what to which thing, and when. Immutable like
 * the incident trail - no setters, every column {@code updatable = false}, and
 * no endpoint that writes or removes one.
 */
@Entity
@Table(name = "admin_audit_entries",
    indexes = @Index(name = "ix_admin_audit_created", columnList = "created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by_id", updatable = false,
        foreignKey = @ForeignKey(name = "fk_admin_audit_performed_by"))
    private User performedBy;

    /** What kind of thing was acted on: USER, TEAM or SLA_POLICY. */
    @Column(name = "target_type", nullable = false, length = 32, updatable = false)
    private String targetType;

    @Column(name = "target_name", nullable = false, length = 160, updatable = false)
    private String targetName;

    @Column(name = "description", nullable = false, length = 500, updatable = false)
    private String description;

    public AdminAuditEntry(User performedBy, String targetType, String targetName, String description) {
        this.performedBy = performedBy;
        this.targetType = targetType;
        this.targetName = targetName;
        this.description = description;
    }
}
