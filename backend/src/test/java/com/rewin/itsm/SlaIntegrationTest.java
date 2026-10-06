package com.rewin.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.rewin.itsm.domain.enums.Priority;
import com.rewin.itsm.domain.enums.RoleName;
import com.rewin.itsm.domain.incident.Incident;
import com.rewin.itsm.domain.sla.SlaPolicy;
import com.rewin.itsm.domain.team.Team;
import com.rewin.itsm.domain.user.User;
import com.rewin.itsm.repository.IncidentRepository;
import com.rewin.itsm.repository.SlaPolicyRepository;
import com.rewin.itsm.repository.TeamRepository;
import com.rewin.itsm.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * SLA behaviour end to end.
 *
 * <p>There is no clock abstraction in V1, so the breach and at-risk cases are
 * produced by moving an incident's stored deadlines into the past rather than by
 * waiting. That exercises the same code path a real breach would.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SlaIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private SlaPolicyRepository slaPolicyRepository;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seed() {
        if (userRepository.existsByEmailIgnoreCase("sla-admin@test.local")) {
            return;
        }
        Team team = teamRepository.save(new Team("SLA Team", "Fixture"));

        // The four default policies, response minutes then resolution minutes.
        // The test database is shared across this suite, so another class may
        // already have activated a policy for one of these priorities. Standing
        // those down first keeps the production invariant - exactly one active
        // policy per priority - which is what these tests are written against.
        activateOnly(new SlaPolicy("Critical - 15m / 4h", Priority.CRITICAL, 15, 240));
        activateOnly(new SlaPolicy("High - 30m / 8h", Priority.HIGH, 30, 480));
        activateOnly(new SlaPolicy("Medium - 1h / 24h", Priority.MEDIUM, 60, 1440));
        activateOnly(new SlaPolicy("Low - 4h / 72h", Priority.LOW, 240, 4320));

        save("sla-admin@test.local", "SLA Admin", RoleName.ADMIN, null);
        save("sla-agent@test.local", "SLA Agent", RoleName.SUPPORT_AGENT, team);
        save("sla-employee@test.local", "SLA Employee", RoleName.EMPLOYEE, null);
    }

    private void activateOnly(SlaPolicy policy) {
        // Saved back explicitly: this class is not transactional, so the entities
        // that come out of the repository are detached and mutating them alone
        // would never reach the database.
        slaPolicyRepository.findAllByPriorityAndActiveTrue(policy.getPriority())
            .forEach(existing -> {
                existing.setActive(false);
                slaPolicyRepository.saveAndFlush(existing);
            });
        slaPolicyRepository.saveAndFlush(policy);
    }

    private void save(String email, String name, RoleName role, Team team) {
        User user = new User(email, passwordEncoder.encode(PASSWORD), name, role);
        user.setTeam(team);
        userRepository.save(user);
    }

    // ------------------------------------------------------ deadlines

    @Test
    void lowAndHighIncidentsGetDifferentDeadlinesFromTheirPolicies() throws Exception {
        JsonNode low = detail(adminToken(), create("Low priority request", "LOW"));
        JsonNode high = detail(adminToken(), create("High priority outage", "HIGH"));

        assertThat(low.get("slaPolicyName").asText()).isEqualTo("Low - 4h / 72h");
        assertThat(high.get("slaPolicyName").asText()).isEqualTo("High - 30m / 8h");

        Instant lowCreated = Instant.parse(low.get("createdAt").asText());
        Instant highCreated = Instant.parse(high.get("createdAt").asText());

        // 4h response / 72h resolution vs 30m / 8h, measured from creation.
        assertThat(minutesBetween(lowCreated, low.get("responseDeadline"))).isEqualTo(240);
        assertThat(minutesBetween(lowCreated, low.get("resolutionDeadline"))).isEqualTo(4320);
        assertThat(minutesBetween(highCreated, high.get("responseDeadline"))).isEqualTo(30);
        assertThat(minutesBetween(highCreated, high.get("resolutionDeadline"))).isEqualTo(480);

        assertThat(low.get("slaStatus").asText()).isEqualTo("ON_TRACK");
        assertThat(high.get("slaStatus").asText()).isEqualTo("ON_TRACK");
    }

    @Test
    void aPriorityChangeRestampsBothDeadlinesFromTheOriginalCreationTime() throws Exception {
        String id = create("Escalating fault", "LOW");
        JsonNode before = detail(adminToken(), id);

        mockMvc.perform(put("/api/incidents/" + id)
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"priority":"CRITICAL"}"""))
            .andExpect(status().isOk());

        JsonNode after = detail(adminToken(), id);
        assertThat(after.get("slaPolicyName").asText()).isEqualTo("Critical - 15m / 4h");
        // Still measured from creation, not restarted from now.
        assertThat(after.get("createdAt").asText()).isEqualTo(before.get("createdAt").asText());
        assertThat(minutesBetween(Instant.parse(after.get("createdAt").asText()),
            after.get("resolutionDeadline"))).isEqualTo(240);
    }

    // --------------------------------------------------------- statuses

    @Test
    void anIncidentWithMostOfItsWindowLeftIsOnTrack() throws Exception {
        JsonNode incident = detail(adminToken(), create("Plenty of time", "LOW"));
        assertThat(incident.get("slaStatus").asText()).isEqualTo("ON_TRACK");
    }

    @Test
    void anIncidentInsideTheLastQuarterOfItsWindowIsAtRisk() throws Exception {
        String id = create("Running late", "LOW");
        // The LOW policy allows 72h to resolve; leave 1h of it, with the first
        // response already given so only the resolution clock drives the status.
        reshapeWindow(UUID.fromString(id), Duration.ofHours(1), true);

        assertThat(detail(adminToken(), id).get("slaStatus").asText()).isEqualTo("AT_RISK");
    }

    @Test
    void anOverdueResolutionReadsAsBreached() throws Exception {
        String id = create("Nobody picked this up", "HIGH");
        backdateDeadlines(UUID.fromString(id), Duration.ofMinutes(30));

        assertThat(detail(adminToken(), id).get("slaStatus").asText()).isEqualTo("BREACHED");
    }

    @Test
    void aMissedResponseKeepsTheIncidentBreachedEvenAfterAnOnTimeResolution() throws Exception {
        String id = create("Answered far too late", "HIGH");
        // Response deadline has passed; the resolution deadline has not.
        setDeadlines(UUID.fromString(id),
            Instant.now().minus(Duration.ofMinutes(10)),
            Instant.now().plus(Duration.ofHours(6)));

        resolve(id, "Fixed well inside the resolution window");

        JsonNode resolved = detail(adminToken(), id);
        assertThat(resolved.get("status").asText()).isEqualTo("RESOLVED");
        // The resolution was on time, the first response was not.
        assertThat(resolved.get("slaStatus").asText()).isEqualTo("BREACHED");
    }

    @Test
    void resolvingBeforeTheDeadlineCompletesTheSla() throws Exception {
        String id = create("Handled quickly", "HIGH");
        resolve(id, "Restarted the service");

        JsonNode resolved = detail(adminToken(), id);
        assertThat(resolved.get("slaStatus").asText()).isEqualTo("COMPLETED");
        assertThat(resolved.get("resolutionCompletedAt").asText()).isNotBlank();
        assertThat(resolved.get("responseCompletedAt").asText()).isNotBlank();
    }

    @Test
    void closingKeepsTheCompletedSlaStatus() throws Exception {
        String id = create("Handled and closed", "HIGH");
        resolve(id, "Replaced the switch");

        mockMvc.perform(patch("/api/incidents/" + id + "/close")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.slaStatus").value("COMPLETED"));
    }

    @Test
    void assigningAnAgentStopsTheResponseClock() throws Exception {
        String id = create("Needs triage", "HIGH");
        assertThat(detail(adminToken(), id).get("responseCompletedAt")).isNull();

        String agentId = objectMapper.readTree(login("sla-agent@test.local")).get("userId").asText();
        mockMvc.perform(patch("/api/incidents/" + id + "/assign")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"agentId\":\"" + agentId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.responseCompletedAt").exists());
    }

    @Test
    void anAgentCommentStopsTheResponseClockButAReporterCommentDoesNot() throws Exception {
        String employeeCommented = create("Reporter chases", "HIGH");
        comment(employeeToken(), employeeCommented, "Any news?");
        assertThat(detail(adminToken(), employeeCommented).get("responseCompletedAt")).isNull();

        String agentCommented = create("Agent replies", "HIGH");
        comment(adminToken(), agentCommented, "Looking into it now");
        assertThat(detail(adminToken(), agentCommented).get("responseCompletedAt")).isNotNull();
    }

    // ------------------------------------------------- role visibility

    @Test
    void anEmployeeSeesTheSlaStatusAndResolutionDeadlineButNotTheResponseStage() throws Exception {
        String id = create("What the reporter sees", "HIGH");

        JsonNode asEmployee = detail(employeeToken(), id);
        assertThat(asEmployee.get("slaStatus").asText()).isEqualTo("ON_TRACK");
        assertThat(asEmployee.get("resolutionDeadline").asText()).isNotBlank();
        assertThat(asEmployee.get("responseDeadline")).isNull();
        assertThat(asEmployee.get("responseCompletedAt")).isNull();

        JsonNode asAgent = detail(agentToken(), id);
        assertThat(asAgent.get("responseDeadline").asText()).isNotBlank();
    }

    // ------------------------------------------- policy administration

    @Test
    void anAdminCreatesEditsAndDeactivatesAPolicy() throws Exception {
        String created = mockMvc.perform(post("/api/sla-policies")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Medium - express","priority":"MEDIUM","responseTimeMinutes":20,
                     "resolutionTimeMinutes":120,"active":true}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.active").value(true))
            .andReturn().getResponse().getContentAsString();
        String policyId = objectMapper.readTree(created).get("id").asText();

        // Activating it must have stood the original MEDIUM policy down.
        assertThat(activeCountFor("MEDIUM")).isEqualTo(1);

        // A new MEDIUM incident now picks up the new policy.
        assertThat(detail(adminToken(), create("Medium after policy change", "MEDIUM"))
            .get("slaPolicyName").asText()).isEqualTo("Medium - express");

        mockMvc.perform(patch("/api/sla-policies/" + policyId + "/active?active=false")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        assertThat(activeCountFor("MEDIUM")).isZero();

        // With no active policy the incident is created without an SLA rather
        // than being rejected or silently treated as met.
        JsonNode unpoliced = detail(adminToken(), create("Medium with no policy", "MEDIUM"));
        assertThat(unpoliced.get("slaStatus")).isNull();
        assertThat(unpoliced.get("resolutionDeadline")).isNull();
    }

    @Test
    void aPolicyWhoseResponseTimeExceedsItsResolutionTimeIsRejected() throws Exception {
        mockMvc.perform(post("/api/sla-policies")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Nonsense","priority":"LOW","responseTimeMinutes":500,
                     "resolutionTimeMinutes":100,"active":false}"""))
            .andExpect(status().isBadRequest());
    }

    @Test
    void policyWritesAreAdminOnlyButReadsAreNot() throws Exception {
        mockMvc.perform(get("/api/sla-policies").header("Authorization", "Bearer " + agentToken()))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/sla-policies")
                .header("Authorization", "Bearer " + agentToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Agent policy","priority":"LOW","responseTimeMinutes":5,
                     "resolutionTimeMinutes":50,"active":true}"""))
            .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ helpers

    /** Moves both deadlines into the past, so the incident is genuinely overdue. */
    @Transactional
    void backdateDeadlines(UUID id, Duration by) {
        Incident incident = incidentRepository.findById(id).orElseThrow();
        incident.setResponseDeadline(Instant.now().minus(by));
        incident.setResolutionDeadline(Instant.now().minus(by));
        incidentRepository.saveAndFlush(incident);
    }

    @Transactional
    void setDeadlines(UUID id, Instant response, Instant resolution) {
        Incident incident = incidentRepository.findById(id).orElseThrow();
        incident.setResponseDeadline(response);
        incident.setResolutionDeadline(resolution);
        incidentRepository.saveAndFlush(incident);
    }

    /**
     * Leaves {@code remaining} of the resolution window, optionally marking the
     * first response as already given.
     */
    @Transactional
    void reshapeWindow(UUID id, Duration remaining, boolean responded) {
        Incident incident = incidentRepository.findById(id).orElseThrow();
        incident.setResolutionDeadline(Instant.now().plus(remaining));
        incident.setResponseDeadline(Instant.now().minusSeconds(1));
        if (responded) {
            // Answered before its deadline, so the response stage is not breached.
            incident.setResponseCompletedAt(incident.getResponseDeadline().minusSeconds(60));
        }
        incidentRepository.saveAndFlush(incident);
    }

    private long activeCountFor(String priority) {
        return slaPolicyRepository.findAllByPriorityAndActiveTrue(Priority.valueOf(priority)).size();
    }

    private long minutesBetween(Instant from, JsonNode deadline) {
        return Duration.between(from, Instant.parse(deadline.asText())).toMinutes();
    }

    private String create(String title, String priority) throws Exception {
        String body = mockMvc.perform(post("/api/incidents")
                .header("Authorization", "Bearer " + employeeToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\",\"description\":\"Details\","
                    + "\"category\":\"Network\",\"priority\":\"" + priority + "\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private void resolve(String id, String summary) throws Exception {
        mockMvc.perform(patch("/api/incidents/" + id + "/resolve")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolutionSummary\":\"" + summary + "\"}"))
            .andExpect(status().isOk());
    }

    private void comment(String token, String id, String body) throws Exception {
        mockMvc.perform(post("/api/incidents/" + id + "/comments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"" + body + "\"}"))
            .andExpect(status().isCreated());
    }

    private JsonNode detail(String token, String id) throws Exception {
        String body = mockMvc.perform(get("/api/incidents/" + id)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private String login(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    private String tokenFor(String email) throws Exception {
        return objectMapper.readTree(login(email)).get("token").asText();
    }

    private String adminToken() throws Exception {
        return tokenFor("sla-admin@test.local");
    }

    private String agentToken() throws Exception {
        return tokenFor("sla-agent@test.local");
    }

    private String employeeToken() throws Exception {
        return tokenFor("sla-employee@test.local");
    }
}
