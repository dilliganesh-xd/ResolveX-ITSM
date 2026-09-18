package com.rewin.itsm.domain.notification;

import com.rewin.itsm.common.BaseEntity;
import com.rewin.itsm.domain.enums.NotificationType;
import com.rewin.itsm.domain.incident.Incident;
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
 * One in-app notification, addressed to exactly one person.
 *
 * <p>The recipient is part of the row rather than derived at read time, so a
 * query for "my notifications" is a filter on an indexed column and cannot
 * accidentally return somebody else's.
 *
 * <p>Only {@code read} can change after creation; the text is a record of what
 * was true when the event happened, so re-reading it later does not rewrite it.
 */
@Entity
@Table(name = "notifications",
    indexes = {
        @Index(name = "ix_notifications_recipient", columnList = "recipient_id"),
        @Index(name = "ix_notifications_recipient_read", columnList = "recipient_id, read_flag")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false, updatable = false,
        foreignKey = @ForeignKey(name = "fk_notifications_recipient"))
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32, updatable = false)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 160, updatable = false)
    private String title;

    @Column(name = "message", nullable = false, length = 500, updatable = false)
    private String message;

    /** The incident this is about, when there is one. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_incident_id", updatable = false,
        foreignKey = @ForeignKey(name = "fk_notifications_incident"))
    private Incident relatedIncident;

    /** Named read_flag because "read" is reserved in several databases. */
    @Column(name = "read_flag", nullable = false)
    private boolean read;

    public Notification(User recipient, NotificationType type, String title, String message,
                        Incident relatedIncident) {
        this.recipient = recipient;
        this.type = type;
        this.title = title;
        this.message = message;
        this.relatedIncident = relatedIncident;
    }

    public void markRead() {
        this.read = true;
    }
}
