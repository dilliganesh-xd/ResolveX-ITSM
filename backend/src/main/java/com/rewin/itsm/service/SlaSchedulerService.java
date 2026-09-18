package com.rewin.itsm.service;

import com.rewin.itsm.service.SlaSweepService.SweepResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * The timer that drives the SLA sweep.
 *
 * <p>Without it, an SLA status is only as current as the last time somebody
 * opened the incident - an incident nobody looks at breaches silently. This job
 * walks the open incidents on a fixed interval and lets the existing SLA rules
 * judge each one.
 *
 * <p>It contains no SLA logic and no database work: it calls
 * {@link SlaSweepService} and logs the result. That separation is not cosmetic -
 * the sweep's transaction only applies when it is invoked across beans - and it
 * is also what makes the schedule replaceable. Moving to an external worker
 * later means replacing this class alone.
 *
 * <p><strong>Single instance assumed.</strong> Two application instances would
 * both sweep; the breach markers stop that duplicating activities, but the work
 * would be done twice. A shared lock belongs with a real distributed scheduler.
 */
@Service
@ConditionalOnProperty(name = "sla.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SlaSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SlaSchedulerService.class);

    private final SlaSweepService sweepService;

    public SlaSchedulerService(SlaSweepService sweepService) {
        this.sweepService = sweepService;
    }

    /**
     * {@code fixedDelay} rather than {@code fixedRate}: the next sweep starts
     * after the previous one finishes, so a slow pass cannot stack up behind
     * itself.
     */
    @Scheduled(
        fixedDelayString = "${sla.scheduler.interval-ms:60000}",
        initialDelayString = "${sla.scheduler.initial-delay-ms:15000}")
    public SweepResult scheduledSweep() {
        SweepResult result = sweepService.sweep();

        // Quiet when nothing moved: this runs every minute, and a line a minute
        // saying "nothing happened" is how logs stop being read.
        if (result.changedAnything()) {
            log.info("SLA sweep completed: checked={}, updated={}, breached={}",
                result.checked(), result.updated(), result.breached());
        } else if (log.isDebugEnabled()) {
            log.debug("SLA sweep completed: checked={}, no changes", result.checked());
        }
        return result;
    }
}
