package com.rewin.itsm.service;

import com.rewin.itsm.domain.enums.IncidentStatus;
import com.rewin.itsm.domain.enums.Priority;
import com.rewin.itsm.domain.enums.SlaStatus;
import com.rewin.itsm.domain.incident.Incident;
import com.rewin.itsm.domain.sla.SlaPolicy;
import com.rewin.itsm.repository.SlaPolicyRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * SLA calculation, entirely in application code for the MVP.
 *
 * <p>This is the only implementation of the SLA rules. The read path and the
 * background sweep both come through {@link #refreshAndRecord}, so an incident
 * cannot be judged one way when someone opens it and another way when the
 * scheduler passes over it.
 *
 * <p>An incident carries two clocks: a response clock that stops at the first
 * agent action, and a resolution clock that stops when it is resolved. Both
 * deadlines are stamped from the active policy at creation, not resolved on
 * read, so editing a policy later cannot retroactively change whether a past
 * incident breached.
 *
 * <p>{@code slaStatus} is a single field summarising both clocks, and it takes
 * the worst outcome. If either deadline was missed - still outstanding past due,
 * or met only after the deadline - the incident reads BREACHED, because a
 * resolution that arrived on time does not undo a first response that never did.
 */
@Service
public class SlaService {

    /** At or below this fraction of the window remaining, a stage reads AT_RISK. */
    private static final double AT_RISK_THRESHOLD = 0.25;

    private final SlaPolicyRepository slaPolicyRepository;
    private final IncidentAuditService audit;
    private final NotificationService notifications;
    private final Clock clock;

    public SlaService(SlaPolicyRepository slaPolicyRepository, IncidentAuditService audit,
                      NotificationService notifications, Clock clock) {
        this.slaPolicyRepository = slaPolicyRepository;
        this.audit = audit;
        this.notifications = notifications;
        this.clock = clock;
    }

    /**
     * Stamps the policy and both deadlines. Having no active policy for a
     * priority is not an error: the incident simply carries no SLA, and every
     * SLA field reads as unknown rather than as met.
     */
    public void applyOnCreate(Incident incident, Priority priority) {
        SlaPolicy policy = slaPolicyRepository.findFirstByPriorityAndActiveTrue(priority).orElse(null);
        if (policy == null) {
            incident.setSlaPolicy(null);
            incident.setResponseDeadline(null);
            incident.setResolutionDeadline(null);
            incident.setSlaStatus(null);
            return;
        }

        Instant from = incident.getCreatedAt() != null ? incident.getCreatedAt() : now();
        incident.setSlaPolicy(policy);
        incident.setResponseDeadline(from.plus(Duration.ofMinutes(policy.getResponseTimeMinutes())));
        incident.setResolutionDeadline(from.plus(Duration.ofMinutes(policy.getResolutionTimeMinutes())));
        incident.setSlaStatus(SlaStatus.ON_TRACK);
    }

    /**
     * Recomputes both deadlines after a priority change, measured from the
     * original creation time rather than from now - the incident has been open
     * that whole time, and restarting the clock would hide it.
     *
     * <p>The breach markers are cleared with them: the new deadlines are a new
     * commitment, and a breach of the old one should not keep the incident
     * silent about missing the new one.
     */
    public void recalculateForPriorityChange(Incident incident, Priority priority) {
        applyOnCreate(incident, priority);
        incident.setResponseBreachedAt(null);
        incident.setResolutionBreachedAt(null);
        refreshAndRecord(incident);
    }

    /** Stops the response clock at the first agent action, if it is still running. */
    public void markResponded(Incident incident) {
        if (incident.getResponseCompletedAt() == null) {
            incident.setResponseCompletedAt(now());
        }
    }

    /** Stops the resolution clock. */
    public void markResolved(Incident incident, Instant resolvedAt) {
        incident.setResolutionCompletedAt(resolvedAt);
        // Resolving without a recorded response still counts as a response.
        markResponded(incident);
    }

    /** Clears the resolution clock when a resolved incident is reopened. */
    public void clearResolution(Incident incident) {
        incident.setResolutionCompletedAt(null);
    }

    /**
     * Recomputes the SLA status and writes any activity the change deserves.
     *
     * <p>Three things can be announced, each at most once for a given state of
     * the incident:
     * <ul>
     *   <li>a response breach and a resolution breach, guarded by their own
     *       timestamp markers, so a sweep running every minute announces each
     *       stage a single time;</li>
     *   <li>entering AT_RISK, guarded by the stored status, which then equals
     *       AT_RISK on every later pass;</li>
     *   <li>meeting the SLA, likewise guarded by the stored status.</li>
     * </ul>
     *
     * @return true when the stored SLA status changed, which is what the sweep
     *         counts as an update.
     */
    public boolean refreshAndRecord(Incident incident) {
        SlaStatus before = incident.getSlaStatus();
        recordStageBreaches(incident);
        refreshStatus(incident);
        SlaStatus after = incident.getSlaStatus();

        if (after == before) {
            return false;
        }
        if (after == SlaStatus.AT_RISK) {
            audit.slaAtRisk(incident);
            notifications.slaAtRisk(incident);
        } else if (after == SlaStatus.COMPLETED) {
            audit.slaCompleted(incident);
        }
        return true;
    }

    /**
     * Announces each stage that has just breached, once.
     *
     * <p>A stage that was answered late is a breach too, and is announced the
     * same way - the marker is about whether we have said it, not about whether
     * the work is still outstanding.
     */
    private void recordStageBreaches(Incident incident) {
        Instant now = now();

        if (incident.getResponseBreachedAt() == null
                && stageBreached(incident.getResponseDeadline(), incident.getResponseCompletedAt(), now)) {
            incident.setResponseBreachedAt(now);
            audit.slaStageBreached(incident, "Response");
            // Told at the same moment it is recorded, so the sweep that notices
            // the breach is the thing that raises it.
            notifications.slaBreached(incident, "Response");
        }

        if (incident.getResolutionBreachedAt() == null
                && stageBreached(incident.getResolutionDeadline(), incident.getResolutionCompletedAt(), now)) {
            incident.setResolutionBreachedAt(now);
            audit.slaStageBreached(incident, "Resolution");
            notifications.slaBreached(incident, "Resolution");
        }
    }

    /**
     * Recomputes {@link Incident#getSlaStatus()} against the clock, writing
     * nothing. Callers that want the transitions recorded use
     * {@link #refreshAndRecord}.
     */
    public void refreshStatus(Incident incident) {
        if (incident.getResolutionDeadline() == null && incident.getResponseDeadline() == null) {
            incident.setSlaStatus(null);
            return;
        }

        Instant now = now();
        boolean responseBreached = stageBreached(incident.getResponseDeadline(), incident.getResponseCompletedAt(), now);
        boolean resolutionBreached =
            stageBreached(incident.getResolutionDeadline(), incident.getResolutionCompletedAt(), now);

        if (responseBreached || resolutionBreached) {
            incident.setSlaStatus(SlaStatus.BREACHED);
            return;
        }

        boolean closedOut = incident.getStatus() == IncidentStatus.RESOLVED
            || incident.getStatus() == IncidentStatus.CLOSED;
        if (closedOut && incident.getResolutionCompletedAt() != null) {
            incident.setSlaStatus(SlaStatus.COMPLETED);
            return;
        }

        // The open stage drives the status: response until it is answered, then
        // resolution. Whichever is tighter wins if both are still running.
        SlaPolicy policy = incident.getSlaPolicy();
        SlaStatus responseStage = incident.getResponseCompletedAt() == null
            ? stageStatus(incident, incident.getResponseDeadline(),
                policy != null ? policy.getResponseTimeMinutes() : 0, now)
            : null;
        SlaStatus resolutionStage = incident.getResolutionCompletedAt() == null
            ? stageStatus(incident, incident.getResolutionDeadline(),
                policy != null ? policy.getResolutionTimeMinutes() : 0, now)
            : null;

        if (responseStage == SlaStatus.AT_RISK || resolutionStage == SlaStatus.AT_RISK) {
            incident.setSlaStatus(SlaStatus.AT_RISK);
        } else if (responseStage != null || resolutionStage != null) {
            incident.setSlaStatus(SlaStatus.ON_TRACK);
        } else {
            incident.setSlaStatus(SlaStatus.COMPLETED);
        }
    }

    /**
     * The verdict for one stage, for display. Same rules as the overall status,
     * asked one clock at a time - so the UI can say which stage is in trouble
     * without working anything out for itself.
     */
    public SlaStatus responseStageStatus(Incident incident) {
        return stageVerdict(incident, incident.getResponseDeadline(), incident.getResponseCompletedAt(),
            incident.getSlaPolicy() != null ? incident.getSlaPolicy().getResponseTimeMinutes() : 0);
    }

    public SlaStatus resolutionStageStatus(Incident incident) {
        return stageVerdict(incident, incident.getResolutionDeadline(), incident.getResolutionCompletedAt(),
            incident.getSlaPolicy() != null ? incident.getSlaPolicy().getResolutionTimeMinutes() : 0);
    }

    private SlaStatus stageVerdict(Incident incident, Instant deadline, Instant completedAt, int windowMinutes) {
        if (deadline == null) {
            return null;
        }
        Instant now = now();
        if (stageBreached(deadline, completedAt, now)) {
            return SlaStatus.BREACHED;
        }
        if (completedAt != null) {
            return SlaStatus.COMPLETED;
        }
        return stageStatus(incident, deadline, windowMinutes, now);
    }

    /** A stage is breached when its deadline passed before the action happened. */
    private boolean stageBreached(Instant deadline, Instant completedAt, Instant now) {
        if (deadline == null) {
            return false;
        }
        return completedAt == null ? now.isAfter(deadline) : completedAt.isAfter(deadline);
    }

    /**
     * AT_RISK once a quarter or less of the stage's window is left.
     *
     * <p>The window comes from the policy, not from createdAt-to-deadline. Those
     * are the same number for an untouched incident, but they diverge the moment
     * a deadline is adjusted, and "25% of the SLA time" means 25% of what the
     * policy actually allows.
     */
    private SlaStatus stageStatus(Incident incident, Instant deadline, int windowMinutes, Instant now) {
        if (deadline == null) {
            return null;
        }
        long total = windowMinutes > 0
            ? Duration.ofMinutes(windowMinutes).toSeconds()
            : Duration.between(incident.getCreatedAt() != null ? incident.getCreatedAt() : now, deadline).toSeconds();
        long left = Duration.between(now, deadline).toSeconds();
        if (total <= 0) {
            return SlaStatus.AT_RISK;
        }
        return (double) left / total <= AT_RISK_THRESHOLD ? SlaStatus.AT_RISK : SlaStatus.ON_TRACK;
    }

    private Instant now() {
        return clock.instant();
    }
}
