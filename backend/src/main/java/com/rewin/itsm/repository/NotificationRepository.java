package com.rewin.itsm.repository;

import com.rewin.itsm.domain.enums.NotificationType;
import com.rewin.itsm.domain.notification.Notification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** Every read is scoped to the recipient; there is no unscoped list query. */
    Page<Notification> findAllByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    List<Notification> findAllByRecipientIdAndReadFalse(UUID recipientId);

    long countByRecipientIdAndReadFalse(UUID recipientId);

    /**
     * Used to keep a repeated event from notifying the same person twice about
     * the same thing - an SLA sweep passing over a breached incident again, for
     * instance.
     */
    boolean existsByRecipientIdAndTypeAndRelatedIncidentId(UUID recipientId, NotificationType type,
                                                           UUID incidentId);

    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.read = true where n.recipient.id = :recipientId and n.read = false")
    int markAllRead(UUID recipientId);
}
