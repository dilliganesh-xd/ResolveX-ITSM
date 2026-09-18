package com.rewin.itsm;

import static org.assertj.core.api.Assertions.assertThat;

import com.rewin.itsm.domain.enums.ActivityType;
import com.rewin.itsm.domain.enums.IncidentStatus;
import com.rewin.itsm.domain.enums.Priority;
import com.rewin.itsm.domain.enums.RoleName;
import com.rewin.itsm.domain.enums.SlaStatus;
import com.rewin.itsm.domain.incident.Incident;
import com.rewin.itsm.domain.incident.IncidentActivity;
import com.rewin.itsm.domain.sla.SlaPolicy;
import com.rewin.itsm.domain.user.User;
import com.rewin.itsm.repository.IncidentActivityRepository;
import com.rewin.itsm.repository.IncidentRepository;
import com.rewin.itsm.repository.SlaPolicyRepository;
import com.rewin.itsm.repository.UserRepository;
import com.rewin.itsm.service.SlaSchedulerService;
import com.rewin.itsm.service.SlaSweepService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * The SLA sweep, driven against a clock the test controls.
 *
 * <p>Nothing here waits. Time is moved forward by replacing the application
 * clock, so a four-hour deadline can be crossed in a millisecond and the
 * assertions stay deterministic.
 *
 * <p>The scheduler is disabled in the test profile, so every sweep in this class
 * is one the test asked for - no timer runs alongside the assertions.
 *
 * <p>Every case drives {@code scheduledSweep()}, the method the timer actually
 * calls, rather than the worker underneath it. An earlier version of these tests
 * called the worker directly and passed while the real scheduled path was
 * writing a duplicate breach every pass, because calling the worker through its
 * own bean gave it the transaction that the scheduled path was missing.
 */
@SpringBootTest(properties = "sla.scheduler.enabled=true")
@ActiveProfiles("test")
class SlaSchedulerIntegrationTest {

    /** A clock the test can move. Shared with the application context. */
    static class MutableClock extends Clock {
        private Instant instant = Instant.now();

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        void advance(Duration by) {
            instant = instant.plus(by);
        }

        void reset() {
            instant = Instant.now();
        }
    }

    @TestConfiguration
    static class ClockOverride {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Autowired private SlaSchedulerService scheduler;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private IncidentActivityRepository activityRepository;
    @Autowired private SlaPolicyRepository slaPolicyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MutableClock clock;

    private User reporter;
    private User agent;

    @BeforeEach
    void seed() {
        clock.reset();
        reporter = userRepository.findByEmailIgnoreCase("sweep-employee@test.local").orElseGet(() ->
            userRepository.save(new User("sweep-employee@test.local",
                passwordEncoder.encode("Password123!"), "Sweep Employee", RoleName.EMPLOYEE)));
        agent = userRepository.findByEmailIgnoreCase("sweep-agent@test.local").orElseGet(() ->
            userRepository.save(new User("sweep-agent@test.local",
                passwordEncoder.encode("Password123!"), "Sweep Agent", RoleName.SUPPORT_AGENT)));

        if (slaPolicyRepository.findFirstByPriorityAndActiveTrue(Priority.HIGH).isEmpty()) {
            slaPolicyRepository.save(new SlaPolicy("Sweep High - 30m / 8h", Priority.HIGH, 30, 480));
        }
    }

    // ------------------------------------------------------------ statuses

    @Test
    void anIncidentWellInsideItsWindowStaysOnTrack() {
        UUID id = newIncident(Duration.ofMinutes(30), Duration.ofHours(8));

        scheduler.scheduledSweep();

        assertThat(slaStatusOf(id)).isEqualTo(SlaStatus.ON_TRACK);
        assertThat(actionsFor(id)).doesNotContain(ActivityType.SLA_AT_RISK, ActivityType.SLA_BREACHED);
    }

    @Test
    void anIncidentInsideTheLastQuarterOfItsWindowBecomesAtRisk() {
        // Response already given, so only the resolution clock is running.
        UUID id = newIncident(Duration.ofMinutes(30), Duration.ofHours(8));
        respondTo(id);

        // 7h20m into an 8h window leaves 40 minutes: under a quarter.
        clock.advance(Duration.ofMinutes(440));
        scheduler.scheduledSweep();

        assertThat(slaStatusOf(id)).isEqualTo(SlaStatus.AT_RISK);
        assertThat(actionsFor(id)).contains(ActivityType.SLA_AT_RISK);
        assertThat(actionsFor(id)).doesNotContain(ActivityType.SLA_BREACHED);
    }

    @Test
    void anIncidentPastItsDeadlineBecomesBreachedAndIsRecordedOnce() {
        UUID id = newIncident(Duration.ofMinutes(30), Duration.ofHours(8));
        respondTo(id);

        clock.advance(Duration.ofHours(9));
        SlaSweepService.SweepResult first = scheduler.scheduledSweep();

        assertThat(slaStatusOf(id)).isEqualTo(SlaStatus.BREACHED);
        assertThat(first.breached()).isEqualTo(1);
        assertThat(breachActivitiesFor(id)).hasSize(1);
        assertThat(descriptionsFor(id)).anyMatch(d -> d.startsWith("Resolution SLA breached"));
    }

    @Test
    void repeatedSweepsDoNotDuplicateTheBreachActivity() {
        UUID id = newIncident(Duration.ofMinutes(30), Duration.ofHours(8));
        respondTo(id);
        clock.advance(Duration.ofHours(9));

        scheduler.scheduledSweep();
        // Five more minutes of a once-a-minute sweep.
        for (int minute = 0; minute < 5; minute++) {
            clock.advance(Duration.ofMinutes(1));
            SlaSweepService.SweepResult later = scheduler.scheduledSweep();
            assertThat(later.breached()).isZero();
            assertThat(later.updated()).isZero();
        }

        assertThat(breachActivitiesFor(id)).hasSize(1);
        assertThat(slaStatusOf(id)).isEqualTo(SlaStatus.BREACHED);
    }

    @Test
    void theSweepPersistsWhatItDecided() {
        UUID id = newIncident(Duration.ofMinutes(30), Duration.ofHours(8));
        respondTo(id);
        clock.advance(Duration.ofHours(9));

        scheduler.scheduledSweep();

        // Read back from the database, not from a cached entity: an unflushed
        // marker is exactly how the same breach gets announced twice.
        Incident stored = incidentRepository.findById(id).orElseThrow();
        assertThat(stored.getSlaStatus()).isEqualTo(SlaStatus.BREACHED);
        assertThat(stored.getResolutionBreachedAt()).isNotNull();
    }

    @Test
    void aBreachIsAnnouncedOnceEvenWhenNobodyEverOpensTheIncident() {
        UUID id = newIncident(Duration.ofMinutes(30), Duration.ofHours(8));
        respondTo(id);
        clock.advance(Duration.ofHours(9));

        // Twenty passes, no reads in between - the case the timer actually runs.
        for (int pass = 0; pass < 20; pass++) {
            clock.advance(Duration.ofMinutes(1));
            scheduler.scheduledSweep();
        }

        assertThat(breachActivitiesFor(id)).hasSize(1);
    }

    // -------------------------------------------------------------- stages

    @Test
    void anUnansweredIncidentBreachesItsResponseStageFirstAndItsResolutionStageLater() {
        UUID id = newIncident(Duration.ofMinutes(30), Duration.ofHours(8));

        // Past the response deadline, nowhere near the resolution deadline.
        clock.advance(Duration.ofMinutes(31));
        scheduler.scheduledSweep();

        assertThat(slaStatusOf(id)).isEqualTo(SlaStatus.BREACHED);
        assertThat(descriptionsFor(id)).anyMatch(d -> d.startsWith("Response SLA breached"));
        assertThat(descriptionsFor(id)).noneMatch(d -> d.startsWith("Resolution SLA breached"));
        assertThat(breachActivitiesFor(id)).hasSize(1);

        // A description is read by a person, in their own timezone. Every other
        // one in the trail is prose; none of them embeds a machine timestamp,
        // and a raw UTC instant here would read as the wrong time to most of
        // the people looking at it.
        assertThat(descriptionsFor(id)).noneMatch(d -> d.contains("(due "));
        assertThat(descriptionsFor(id)).noneMatch(d -> d.contains("Z)"));

        // Now past the resolution deadline too: a second, different breach.
        clock.advance(Duration.ofHours(8));
        scheduler.scheduledSweep();

        assertThat(descriptionsFor(id)).anyMatch(d -> d.startsWith("Resolution SLA breached"));
        assertThat(breachActivitiesFor(id)).hasSize(2);

        // And still only those two, however many times the sweep runs.
        clock.advance(Duration.ofMinutes(1));
        scheduler.scheduledSweep();
        assertThat(breachActivitiesFor(id)).hasSize(2);
    }

    @Test
    void anAnsweredResponseNeverBreachesItsResponseStage() {
        UUID id = newIncident(Duration.ofMinutes(30), Duration.ofHours(8));
        // Answered ten minutes in, well before the response deadline.
        clock.advance(Duration.ofMinutes(10));
        respondTo(id);

        clock.advance(Duration.ofHours(9));
        scheduler.scheduledSweep();

        assertThat(descriptionsFor(id)).noneMatch(d -> d.startsWith("Response SLA breached"));
        assertThat(descriptionsFor(id)).anyMatch(d -> d.startsWith("Resolution SLA breached"));
    }

    @Test
    void aResolvedIncidentIsNotMarkedResolutionBreachedAfterwards() {
        UUID id = newIncident(Duration.ofMinutes(30), Duration.ofHours(8));
        respondTo(id);
        // Resolved an hour in, comfortably inside the window.
        clock.advance(Duration.ofHours(1));
        resolve(id);

        // Long past the original deadline, but the work was finished in time.
        clock.advance(Duration.ofHours(20));
        scheduler.scheduledSweep();

        assertThat(slaStatusOf(id)).isEqualTo(SlaStatus.COMPLETED);
        assertThat(breachActivitiesFor(id)).isEmpty();
    }

    @Test
    void aClosedIncidentIsIgnoredBySweep() {
        UUID id = newIncident(Duration.ofMinutes(30), Duration.ofHours(8));
        closeWithoutResolving(id);

        SlaStatus before = slaStatusOf(id);
        clock.advance(Duration.ofHours(9));
        scheduler.scheduledSweep();

        // The test database is shared across this suite, so the sweep legitimately
        // checks other incidents. What matters is that this one is not among them
        // and is left exactly as it was.
        assertThat(sweepSawIncident(id)).isFalse();
        assertThat(slaStatusOf(id)).isEqualTo(before);
        assertThat(breachActivitiesFor(id)).isEmpty();
    }

    @Test
    void anIncidentWithNoPolicyCarriesNoSlaAndIsSkipped() {
        UUID id = incidentWithoutSla();

        clock.advance(Duration.ofHours(9));
        scheduler.scheduledSweep();

        assertThat(slaStatusOf(id)).isNull();
        assertThat(breachActivitiesFor(id)).isEmpty();
    }

    @Test
    void theSweepReportsWhatItChecked() {
        newIncident(Duration.ofMinutes(30), Duration.ofHours(8));
        newIncident(Duration.ofMinutes(30), Duration.ofHours(8));

        SlaSweepService.SweepResult result = scheduler.scheduledSweep();

        assertThat(result.checked()).isGreaterThanOrEqualTo(2);
        assertThat(result.updated()).isGreaterThanOrEqualTo(0);
    }

    // ------------------------------------------------------------- helpers

    /**
     * Builds an incident directly, with deadlines measured from the test clock,
     * so each case starts from a known position in its window.
     */
    @Transactional
    UUID newIncident(Duration responseIn, Duration resolutionIn) {
        Incident incident = new Incident();
        incident.setTitle("Sweep fixture");
        incident.setDescription("Fixture");
        incident.setCategory("Network");
        incident.setPriority(Priority.HIGH);
        incident.setStatus(IncidentStatus.NEW);
        incident.setReporter(reporter);
        long seq = incidentRepository.findMaxReferenceSeq() + 1;
        incident.setReferenceSeq(seq);
        incident.setReference(String.format("INC-%06d", seq));

        Incident saved = incidentRepository.saveAndFlush(incident);
        saved.setSlaPolicy(slaPolicyRepository.findFirstByPriorityAndActiveTrue(Priority.HIGH).orElseThrow());
        saved.setResponseDeadline(clock.instant().plus(responseIn));
        saved.setResolutionDeadline(clock.instant().plus(resolutionIn));
        saved.setSlaStatus(SlaStatus.ON_TRACK);
        return incidentRepository.saveAndFlush(saved).getId();
    }

    @Transactional
    UUID incidentWithoutSla() {
        Incident incident = new Incident();
        incident.setTitle("No policy fixture");
        incident.setDescription("Fixture");
        incident.setCategory("Network");
        incident.setPriority(Priority.HIGH);
        incident.setStatus(IncidentStatus.NEW);
        incident.setReporter(reporter);
        long seq = incidentRepository.findMaxReferenceSeq() + 1;
        incident.setReferenceSeq(seq);
        incident.setReference(String.format("INC-%06d", seq));
        return incidentRepository.saveAndFlush(incident).getId();
    }

    @Transactional
    void respondTo(UUID id) {
        Incident incident = incidentRepository.findById(id).orElseThrow();
        incident.setAssignedAgent(agent);
        incident.setStatus(IncidentStatus.ASSIGNED);
        incident.setResponseCompletedAt(clock.instant());
        incidentRepository.saveAndFlush(incident);
    }

    @Transactional
    void resolve(UUID id) {
        Incident incident = incidentRepository.findById(id).orElseThrow();
        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolutionSummary("Fixed");
        incident.setResolvedAt(clock.instant());
        incident.setResolutionCompletedAt(clock.instant());
        incidentRepository.saveAndFlush(incident);
    }

    @Transactional
    void closeWithoutResolving(UUID id) {
        Incident incident = incidentRepository.findById(id).orElseThrow();
        incident.setStatus(IncidentStatus.CLOSED);
        incident.setClosedAt(clock.instant());
        incidentRepository.saveAndFlush(incident);
    }

    private boolean sweepSawIncident(UUID id) {
        return incidentRepository.findSweepCandidates(IncidentStatus.CLOSED).stream()
            .anyMatch(incident -> incident.getId().equals(id));
    }

    private SlaStatus slaStatusOf(UUID id) {
        return incidentRepository.findById(id).orElseThrow().getSlaStatus();
    }

    private List<IncidentActivity> activitiesFor(UUID id) {
        return activityRepository.findAllByIncidentIdOrderByCreatedAtAsc(id);
    }

    private List<ActivityType> actionsFor(UUID id) {
        return activitiesFor(id).stream().map(IncidentActivity::getAction).toList();
    }

    private List<String> descriptionsFor(UUID id) {
        return activitiesFor(id).stream().map(IncidentActivity::getDescription).toList();
    }

    private List<IncidentActivity> breachActivitiesFor(UUID id) {
        return activitiesFor(id).stream()
            .filter(a -> a.getAction() == ActivityType.SLA_BREACHED)
            .toList();
    }
}
