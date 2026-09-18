package com.rewin.itsm.service;

import com.rewin.itsm.domain.enums.IncidentStatus;
import com.rewin.itsm.domain.enums.SlaStatus;
import com.rewin.itsm.domain.incident.Incident;
import com.rewin.itsm.repository.IncidentRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One pass of the SLA sweep.
 *
 * <p>This is a separate bean from {@link SlaSchedulerService} on purpose. Spring
 * applies {@code @Transactional} through a proxy, and a method calling another
 * method on its own instance goes straight to the implementation, proxy and all
 * its transaction handling bypassed. When the timer and the transactional work
 * lived in one class, the sweep ran with no transaction at all: the breach
 * activity was written, but the marker that says "already announced" was left
 * on a detached entity and never flushed - so the next sweep announced the same
 * breach again. Splitting the two keeps the call going through the proxy.
 *
 * <p>It holds no SLA rules of its own. It selects rows, hands each to
 * {@link SlaService#refreshAndRecord}, and counts.
 */
@Service
public class SlaSweepService {

    private final IncidentRepository incidentRepository;
    private final SlaService slaService;

    public SlaSweepService(IncidentRepository incidentRepository, SlaService slaService) {
        this.incidentRepository = incidentRepository;
        this.slaService = slaService;
    }

    /**
     * Re-evaluates every open incident that carries a deadline.
     *
     * <p>Transactional, so the SLA status and the breach markers each incident
     * picks up are flushed on commit. That flush is what makes a breach
     * announced once rather than once per minute.
     */
    @Transactional
    public SweepResult sweep() {
        List<Incident> candidates = incidentRepository.findSweepCandidates(IncidentStatus.CLOSED);

        int updated = 0;
        int breached = 0;
        for (Incident incident : candidates) {
            SlaStatus before = incident.getSlaStatus();
            if (slaService.refreshAndRecord(incident)) {
                updated++;
                if (incident.getSlaStatus() == SlaStatus.BREACHED && before != SlaStatus.BREACHED) {
                    breached++;
                }
            }
        }
        return new SweepResult(candidates.size(), updated, breached);
    }

    /** What one pass of the sweep did. */
    public record SweepResult(int checked, int updated, int breached) {

        public boolean changedAnything() {
            return updated > 0 || breached > 0;
        }
    }
}
