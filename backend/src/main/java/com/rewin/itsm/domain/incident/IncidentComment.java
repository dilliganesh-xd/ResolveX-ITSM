package com.rewin.itsm.domain.incident;

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
import lombok.Setter;

/**
 * A comment on an incident. {@code internal} marks agent-only notes, which are
 * filtered out of the employee's view of the thread.
 */
@Entity
@Table(name = "incident_comments", indexes = @Index(name = "ix_comments_incident", columnList = "incident_id"))
@Getter
@Setter
@NoArgsConstructor
public class IncidentComment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false, foreignKey = @ForeignKey(name = "fk_comments_incident"))
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, foreignKey = @ForeignKey(name = "fk_comments_author"))
    private User author;

    @Column(name = "comment", nullable = false, columnDefinition = "text")
    private String comment;

    @Column(name = "internal", nullable = false)
    private boolean internal = false;
}
