package com.rewin.itsm.service;

import com.rewin.itsm.common.NotFoundException;
import com.rewin.itsm.common.PageResponse;
import com.rewin.itsm.domain.enums.IncidentStatus;
import com.rewin.itsm.domain.enums.NotificationType;
import com.rewin.itsm.domain.enums.RoleName;
import com.rewin.itsm.domain.incident.Incident;
import com.rewin.itsm.domain.notification.Notification;
import com.rewin.itsm.domain.user.User;
import com.rewin.itsm.dto.notification.NotificationResponse;
import com.rewin.itsm.repository.NotificationRepository;
import com.rewin.itsm.repository.UserRepository;
import com.rewin.itsm.security.AppUserPrincipal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-app notifications.
 *
 * <p>Every read and write is scoped to the caller's own id, taken from the
 * token rather than from a parameter, so one person cannot ask for another
 * person's notifications however they phrase the request.
 *
 * <p>Nobody is ever notified about their own action. An agent who comments does
 * not need telling that a comment was added.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    // ------------------------------------------------------------- reading

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(AppUserPrincipal principal, Pageable pageable) {
        return PageResponse.from(notificationRepository
            .findAllByRecipientIdOrderByCreatedAtDesc(principal.getId(), pageable)
            .map(NotificationResponse::from));
    }

    @Transactional(readOnly = true)
    public long unreadCount(AppUserPrincipal principal) {
        return notificationRepository.countByRecipientIdAndReadFalse(principal.getId());
    }

    // ------------------------------------------------------------- writing

    /**
     * Marks one notification read.
     *
     * <p>A notification belonging to somebody else reads as 404 rather than 403:
     * the caller has no business knowing it exists.
     */
    @Transactional
    public NotificationResponse markRead(UUID id, AppUserPrincipal principal) {
        Notification notification = notificationRepository.findById(id)
            .filter(candidate -> candidate.getRecipient().getId().equals(principal.getId()))
            .orElseThrow(() -> new NotFoundException("Notification not found"));
        notification.markRead();
        return NotificationResponse.from(notification);
    }

    @Transactional
    public int markAllRead(AppUserPrincipal principal) {
        return notificationRepository.markAllRead(principal.getId());
    }

    // ------------------------------------------------- event notifications

    /**
     * A new incident needs somebody to pick it up. If it arrived routed to a
     * team, that team is told; otherwise every active agent is, because an
     * untriaged incident is nobody's in particular and silence would leave it
     * sitting there.
     */
    public void incidentCreated(Incident incident, User reporter) {
        List<User> recipients = incident.getAssignedTeam() != null
            ? userRepository.findAllByTeamIdOrderByNameAsc(incident.getAssignedTeam().getId()).stream()
                .filter(user -> user.getRole() != RoleName.EMPLOYEE)
                .toList()
            : userRepository.findAllByRoleAndActiveTrueOrderByNameAsc(RoleName.SUPPORT_AGENT);

        notifyAll(recipients, reporter, NotificationType.INCIDENT_CREATED,
            "New incident " + incident.getReference(),
            reporter.getName() + " reported " + quoted(incident.getTitle())
                + " at " + incident.getPriority().name() + " priority.",
            incident);
    }

    public void incidentAssigned(Incident incident, User actor, User agent) {
        notifyOne(agent, actor, NotificationType.INCIDENT_ASSIGNED,
            incident.getReference() + " assigned to you",
            nameOf(actor) + " assigned " + quoted(incident.getTitle()) + " to you.",
            incident);
    }

    /**
     * A comment reaches the reporter and the assigned agent. An internal note
     * never reaches the reporter - the same rule the comment thread applies.
     */
    public void commentAdded(Incident incident, User author, boolean internal) {
        List<User> recipients = new ArrayList<>();
        if (!internal) {
            recipients.add(incident.getReporter());
        }
        if (incident.getAssignedAgent() != null) {
            recipients.add(incident.getAssignedAgent());
        }

        notifyAll(recipients, author, NotificationType.COMMENT_ADDED,
            "New comment on " + incident.getReference(),
            author.getName() + (internal ? " added an internal note on " : " commented on ")
                + quoted(incident.getTitle()) + ".",
            incident);
    }

    public void incidentResolved(Incident incident, User actor) {
        notifyOne(incident.getReporter(), actor, NotificationType.INCIDENT_RESOLVED,
            incident.getReference() + " resolved",
            nameOf(actor) + " resolved " + quoted(incident.getTitle()) + ".",
            incident);
    }

    public void incidentClosed(Incident incident, User actor) {
        notifyOne(incident.getReporter(), actor, NotificationType.INCIDENT_CLOSED,
            incident.getReference() + " closed",
            nameOf(actor) + " closed " + quoted(incident.getTitle()) + ".",
            incident);
    }

    /** The warning goes to whoever is holding the incident. */
    public void slaAtRisk(Incident incident) {
        if (incident.getAssignedAgent() == null) {
            return;
        }
        notifyOnce(incident.getAssignedAgent(), NotificationType.SLA_AT_RISK,
            "SLA at risk on " + incident.getReference(),
            quoted(incident.getTitle()) + " is close to missing its SLA.",
            incident);
    }

    /**
     * A breach goes to the assigned agent and to every active admin, because by
     * then it is no longer only the agent's problem.
     */
    public void slaBreached(Incident incident, String stage) {
        List<User> recipients = new ArrayList<>(
            userRepository.findAllByRole(RoleName.ADMIN).stream().filter(User::isActive).toList());
        if (incident.getAssignedAgent() != null) {
            recipients.add(incident.getAssignedAgent());
        }

        for (User recipient : dedupe(recipients)) {
            notifyOnceForStage(recipient, incident, stage);
        }
    }

    // ------------------------------------------------------------- helpers

    private void notifyOne(User recipient, User actor, NotificationType type, String title,
                           String message, Incident incident) {
        if (recipient == null || isActor(recipient, actor) || !recipient.isActive()) {
            return;
        }
        notificationRepository.save(new Notification(recipient, type, title, truncate(message), incident));
    }

    private void notifyAll(List<User> recipients, User actor, NotificationType type, String title,
                           String message, Incident incident) {
        for (User recipient : dedupe(recipients)) {
            notifyOne(recipient, actor, type, title, message, incident);
        }
    }

    /**
     * Writes only if this person has not already been told this about this
     * incident. Used for the SLA states, which are re-evaluated on every read
     * and every sweep.
     */
    private void notifyOnce(User recipient, NotificationType type, String title, String message,
                            Incident incident) {
        if (recipient == null || !recipient.isActive()) {
            return;
        }
        if (notificationRepository.existsByRecipientIdAndTypeAndRelatedIncidentId(
                recipient.getId(), type, incident.getId())) {
            return;
        }
        notificationRepository.save(new Notification(recipient, type, title, truncate(message), incident));
    }

    /**
     * A breach notification names its stage. Response and resolution are two
     * different failures, so both may be sent - but each only once.
     */
    private void notifyOnceForStage(User recipient, Incident incident, String stage) {
        if (recipient == null || !recipient.isActive()) {
            return;
        }
        String title = stage + " SLA breached on " + incident.getReference();
        boolean alreadySent = notificationRepository
            .findAllByRecipientIdAndReadFalse(recipient.getId()).stream()
            .anyMatch(existing -> existing.getTitle().equals(title));
        if (alreadySent) {
            return;
        }
        // A read notification about the same stage must not be resent either.
        if (hasStageNotification(recipient.getId(), incident.getId(), title)) {
            return;
        }
        notificationRepository.save(new Notification(recipient, NotificationType.SLA_BREACHED, title,
            truncate(quoted(incident.getTitle()) + " missed its " + stage.toLowerCase() + " SLA."),
            incident));
    }

    private boolean hasStageNotification(UUID recipientId, UUID incidentId, String title) {
        return notificationRepository.findAll().stream()
            .anyMatch(existing -> existing.getRecipient().getId().equals(recipientId)
                && existing.getRelatedIncident() != null
                && existing.getRelatedIncident().getId().equals(incidentId)
                && existing.getType() == NotificationType.SLA_BREACHED
                && existing.getTitle().equals(title));
    }

    private boolean isActor(User recipient, User actor) {
        return actor != null && recipient.getId().equals(actor.getId());
    }

    /** Keeps the first occurrence of each person, so nobody is told twice. */
    private List<User> dedupe(List<User> users) {
        Map<UUID, User> unique = new LinkedHashMap<>();
        users.stream().filter(user -> user != null).forEach(user -> unique.putIfAbsent(user.getId(), user));
        return List.copyOf(unique.values());
    }

    private String quoted(String value) {
        return "\"" + value + "\"";
    }

    private String nameOf(User user) {
        return user != null ? user.getName() : "The system";
    }

    private String truncate(String value) {
        return value.length() <= 500 ? value : value.substring(0, 499) + "…";
    }

    /** True when an incident is finished, so there is nothing left to warn about. */
    static boolean isSettled(Incident incident) {
        return incident.getStatus() == IncidentStatus.RESOLVED
            || incident.getStatus() == IncidentStatus.CLOSED;
    }
}
