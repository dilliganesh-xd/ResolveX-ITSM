package com.rewin.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewin.itsm.domain.enums.Priority;
import com.rewin.itsm.domain.enums.RoleName;
import com.rewin.itsm.domain.sla.SlaPolicy;
import com.rewin.itsm.domain.team.Team;
import com.rewin.itsm.domain.user.User;
import com.rewin.itsm.repository.SlaPolicyRepository;
import com.rewin.itsm.repository.TeamRepository;
import com.rewin.itsm.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end coverage of login and the role rules, driven through the real
 * filter chain so the JWT filter and the authorization rules are both exercised.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAndRoleIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private SlaPolicyRepository slaPolicyRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seed() {
        if (userRepository.existsByEmailIgnoreCase("admin@test.local")) {
            return;
        }
        Team team = teamRepository.save(new Team("Infrastructure", "Test team"));
        teamRepository.save(new Team("Applications", "Second test team"));
        slaPolicyRepository.save(new SlaPolicy("High - 8h", Priority.HIGH, 30, 480));

        save("admin@test.local", "Test Admin", RoleName.ADMIN, null);
        save("agent@test.local", "Test Agent", RoleName.SUPPORT_AGENT, team);
        save("employee@test.local", "Test Employee", RoleName.EMPLOYEE, null);
        save("other@test.local", "Other Employee", RoleName.EMPLOYEE, null);
    }

    private void save(String email, String name, RoleName role, Team team) {
        User user = new User(email, passwordEncoder.encode(PASSWORD), name, role);
        user.setTeam(team);
        userRepository.save(user);
    }

    // ------------------------------------------------------------ login

    @Test
    void loginSucceedsForEachRoleAndReturnsTheExpectedPayload() throws Exception {
        for (String email : new String[] {"employee@test.local", "agent@test.local", "admin@test.local"}) {
            JsonNode body = objectMapper.readTree(login(email));
            assertThat(body.get("token").asText()).isNotBlank();
            assertThat(body.get("email").asText()).isEqualTo(email);
            assertThat(body.get("userId").asText()).isNotBlank();
            assertThat(body.get("role").asText()).isNotBlank();
            assertThat(body.has("password")).isFalse();
            assertThat(body.has("passwordHash")).isFalse();
        }
    }

    @Test
    void loginWithAWrongPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"admin@test.local","password":"wrong-password"}"""))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithAnInvalidBodyIsRejectedWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"not-an-email","password":""}"""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.email").exists())
            .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    // ------------------------------------------------- protected endpoints

    @Test
    void protectedEndpointsRequireAToken() throws Exception {
        mockMvc.perform(get("/api/incidents")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpointsDoNotRequireAToken() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void aGarbledTokenIsTreatedAsAnonymous() throws Exception {
        mockMvc.perform(get("/api/incidents").header("Authorization", "Bearer not.a.jwt"))
            .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------- role restrictions

    @Test
    void employeeCannotReachAdminEndpoints() throws Exception {
        String token = tokenFor("employee@test.local");
        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reports").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test
    void agentCannotReachAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + tokenFor("agent@test.local")))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReachAdminEndpoints() throws Exception {
        String token = tokenFor("admin@test.local");
        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/reports").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test
    void employeeCanCreateAnIncidentAndSeeItsSlaDeadline() throws Exception {
        String token = tokenFor("employee@test.local");
        String response = mockMvc.perform(post("/api/incidents")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"VPN down","description":"Cannot connect","category":"Network","priority":"HIGH"}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.reference").exists())
            .andExpect(jsonPath("$.status").value("NEW"))
            .andExpect(jsonPath("$.resolutionDeadline").exists())
            .andExpect(jsonPath("$.slaStatus").value("ON_TRACK"))
            .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(response).get("reporterName").asText()).isEqualTo("Test Employee");
    }

    @Test
    void employeeCannotChangeAnIncidentTheyReported() throws Exception {
        String token = tokenFor("employee@test.local");
        String incidentId = createIncident(token, "Printer jam");

        mockMvc.perform(patch("/api/incidents/" + incidentId + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"RESOLVED"}"""))
            .andExpect(status().isForbidden());
    }

    @Test
    void employeeCannotReadAnotherEmployeesIncident() throws Exception {
        String ownerToken = tokenFor("employee@test.local");
        String incidentId = createIncident(ownerToken, "Laptop will not boot");

        mockMvc.perform(get("/api/incidents/" + incidentId)
                .header("Authorization", "Bearer " + tokenFor("other@test.local")))
            .andExpect(status().isForbidden());
    }

    @Test
    void employeeListOnlyContainsTheirOwnIncidents() throws Exception {
        createIncident(tokenFor("employee@test.local"), "Mine");
        createIncident(tokenFor("other@test.local"), "Theirs");

        String body = mockMvc.perform(get("/api/incidents")
                .header("Authorization", "Bearer " + tokenFor("other@test.local")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        JsonNode rows = objectMapper.readTree(body).get("content");
        assertThat(rows).allSatisfy(row ->
            assertThat(row.get("reporterName").asText()).isEqualTo("Other Employee"));
    }

    @Test
    void agentCanTriageAssignAndResolveAnIncident() throws Exception {
        String incidentId = createIncident(tokenFor("employee@test.local"), "Server unreachable");
        String agentToken = tokenFor("agent@test.local");
        String agentId = objectMapper.readTree(login("agent@test.local")).get("userId").asText();

        mockMvc.perform(patch("/api/incidents/" + incidentId + "/assign")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"agentId\":\"" + agentId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ASSIGNED"))
            .andExpect(jsonPath("$.assignedAgentName").value("Test Agent"));

        mockMvc.perform(patch("/api/incidents/" + incidentId + "/status")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"IN_PROGRESS"}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(patch("/api/incidents/" + incidentId + "/resolve")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"resolutionSummary":"Restarted the service"}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RESOLVED"))
            .andExpect(jsonPath("$.resolvedAt").exists())
            .andExpect(jsonPath("$.resolutionSummary").value("Restarted the service"))
            .andExpect(jsonPath("$.slaStatus").value("COMPLETED"));
    }

    @Test
    void anIllegalStatusTransitionIsRejected() throws Exception {
        String adminToken = tokenFor("admin@test.local");
        String incidentId = createIncident(tokenFor("employee@test.local"), "Mailbox full");

        resolve(adminToken, incidentId, "Cleared the mailbox");

        // A resolved incident may only be reopened to IN_PROGRESS.
        mockMvc.perform(patch("/api/incidents/" + incidentId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"ASSIGNED"}"""))
            .andExpect(status().isBadRequest());
    }

    @Test
    void theStatusEndpointRefusesToResolveOrClose() throws Exception {
        String adminToken = tokenFor("admin@test.local");
        String incidentId = createIncident(tokenFor("employee@test.local"), "Shared drive missing");

        mockMvc.perform(patch("/api/incidents/" + incidentId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"RESOLVED"}"""))
            .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/incidents/" + incidentId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"CLOSED"}"""))
            .andExpect(status().isBadRequest());
    }

    @Test
    void resolvingWithoutASummaryIsRejected() throws Exception {
        String incidentId = createIncident(tokenFor("employee@test.local"), "Monitor flickers");

        mockMvc.perform(patch("/api/incidents/" + incidentId + "/resolve")
                .header("Authorization", "Bearer " + tokenFor("admin@test.local"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"resolutionSummary":"  "}"""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.resolutionSummary").exists());
    }

    @Test
    void anOpenIncidentCannotBeClosed() throws Exception {
        String incidentId = createIncident(tokenFor("employee@test.local"), "Keyboard broken");

        mockMvc.perform(patch("/api/incidents/" + incidentId + "/close")
                .header("Authorization", "Bearer " + tokenFor("admin@test.local"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void adminClosesAResolvedIncident() throws Exception {
        String adminToken = tokenFor("admin@test.local");
        String incidentId = createIncident(tokenFor("employee@test.local"), "Badge reader down");

        resolve(adminToken, incidentId, "Replaced the reader");

        mockMvc.perform(patch("/api/incidents/" + incidentId + "/close")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"note":"Confirmed with the reporter"}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CLOSED"))
            .andExpect(jsonPath("$.closedAt").exists());
    }

    @Test
    void anAgentCanTakeAnIncidentThemselves() throws Exception {
        String agentToken = tokenFor("agent@test.local");
        String incidentId = createIncident(tokenFor("employee@test.local"), "Shared printer offline");

        mockMvc.perform(patch("/api/incidents/" + incidentId + "/assign")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"agentId\":\"" + userId("agent@test.local") + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ASSIGNED"))
            .andExpect(jsonPath("$.assignedAgentName").value("Test Agent"));
    }

    @Test
    void anAgentCannotHandTheIncidentToAnotherAgent() throws Exception {
        String incidentId = createIncident(tokenFor("employee@test.local"), "Mailbox full");

        // The queue is a pool: an agent takes work or leaves it. Pointing it at
        // a colleague is a scheduling decision, and belongs to an admin.
        mockMvc.perform(patch("/api/incidents/" + incidentId + "/assign")
                .header("Authorization", "Bearer " + tokenFor("agent@test.local"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"agentId\":\"" + userId("admin@test.local") + "\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void anAgentCannotMoveAnIncidentBetweenTeams() throws Exception {
        String incidentId = createIncident(tokenFor("employee@test.local"), "VPN keeps dropping");

        mockMvc.perform(patch("/api/incidents/" + incidentId + "/assign")
                .header("Authorization", "Bearer " + tokenFor("agent@test.local"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"teamId\":\"" + otherTeamId() + "\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void anAdminCanStillAssignToAnybody() throws Exception {
        String incidentId = createIncident(tokenFor("employee@test.local"), "Laptop will not boot");

        mockMvc.perform(patch("/api/incidents/" + incidentId + "/assign")
                .header("Authorization", "Bearer " + tokenFor("admin@test.local"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"agentId\":\"" + userId("agent@test.local") + "\",\"teamId\":\"" + otherTeamId() + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assignedAgentName").value("Test Agent"));
    }

    @Test
    void aClosedIncidentRefusesEveryFurtherWrite() throws Exception {
        String adminToken = tokenFor("admin@test.local");
        String agentToken = tokenFor("agent@test.local");
        String employeeToken = tokenFor("employee@test.local");
        String incidentId = createIncident(employeeToken, "Printer jammed for the last time");

        // Routed to the agent first, so the agent still has a claim on it once it
        // is closed. Otherwise assertCanView turns them away before the closed
        // check is ever reached, and the assertion below would prove nothing.
        mockMvc.perform(patch("/api/incidents/" + incidentId + "/assign")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"agentId\":\"" + userId("agent@test.local") + "\"}"))
            .andExpect(status().isOk());

        resolve(adminToken, incidentId, "Replaced the fuser");
        mockMvc.perform(patch("/api/incidents/" + incidentId + "/close")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CLOSED"));

        // The screen hides these controls once an incident is closed. The server
        // has to refuse them too, or the hiding is the only thing enforcing it.
        mockMvc.perform(put("/api/incidents/" + incidentId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"priority":"CRITICAL"}"""))
            .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/incidents/" + incidentId + "/assign")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"agentId\":\"" + userId("agent@test.local") + "\"}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/incidents/" + incidentId + "/comments")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"comment":"One more thought"}"""))
            .andExpect(status().isBadRequest());

        // Including from the reporter, who may comment on an open incident.
        mockMvc.perform(post("/api/incidents/" + incidentId + "/comments")
                .header("Authorization", "Bearer " + employeeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"comment":"It is happening again"}"""))
            .andExpect(status().isBadRequest());
    }

    @Test
    void aClosedIncidentKeepsTheValuesItWasClosedWith() throws Exception {
        String adminToken = tokenFor("admin@test.local");
        String incidentId = createIncident(tokenFor("employee@test.local"), "Monitor flickers");

        resolve(adminToken, incidentId, "Swapped the cable");
        mockMvc.perform(patch("/api/incidents/" + incidentId + "/close")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/incidents/" + incidentId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"priority":"CRITICAL","category":"Hardware"}"""))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/incidents/" + incidentId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CLOSED"))
            .andExpect(jsonPath("$.priority").value("HIGH"))
            .andExpect(jsonPath("$.comments.length()").value(0));
    }

    @Test
    void employeeCannotResolveOrCloseAnIncident() throws Exception {
        String employeeToken = tokenFor("employee@test.local");
        String incidentId = createIncident(employeeToken, "Wifi drops");

        mockMvc.perform(patch("/api/incidents/" + incidentId + "/resolve")
                .header("Authorization", "Bearer " + employeeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"resolutionSummary":"I fixed it myself"}"""))
            .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/incidents/" + incidentId + "/close")
                .header("Authorization", "Bearer " + employeeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void reopeningAResolvedIncidentClearsTheResolution() throws Exception {
        String adminToken = tokenFor("admin@test.local");
        String incidentId = createIncident(tokenFor("employee@test.local"), "Phone line dead");

        resolve(adminToken, incidentId, "Rewired the socket");

        mockMvc.perform(patch("/api/incidents/" + incidentId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"IN_PROGRESS","note":"Reporter says it is still dead"}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.resolvedAt").doesNotExist())
            .andExpect(jsonPath("$.resolutionSummary").doesNotExist());
    }

    @Test
    void theActivityTimelineRecordsTheWholeLifecycle() throws Exception {
        String adminToken = tokenFor("admin@test.local");
        String employeeToken = tokenFor("employee@test.local");
        String incidentId = createIncident(employeeToken, "Database timeouts");
        String agentId = objectMapper.readTree(login("agent@test.local")).get("userId").asText();

        mockMvc.perform(patch("/api/incidents/" + incidentId + "/assign")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"agentId\":\"" + agentId + "\"}")).andExpect(status().isOk());

        mockMvc.perform(put("/api/incidents/" + incidentId)
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"priority":"CRITICAL","category":"Database"}""")).andExpect(status().isOk());

        mockMvc.perform(post("/api/incidents/" + incidentId + "/comments")
            .header("Authorization", "Bearer " + employeeToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"comment":"Still timing out"}""")).andExpect(status().isCreated());

        resolve(adminToken, incidentId, "Rebuilt the index");

        String body = mockMvc.perform(patch("/api/incidents/" + incidentId + "/close")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        JsonNode activity = objectMapper.readTree(body).get("activity");
        List<String> types = new ArrayList<>();
        activity.forEach(entry -> types.add(entry.get("action").asText()));

        assertThat(types).containsSubsequence("INCIDENT_CREATED", "INCIDENT_ASSIGNED", "PRIORITY_CHANGED",
            "COMMENT_ADDED", "INCIDENT_RESOLVED", "INCIDENT_CLOSED");
        assertThat(types).contains("INCIDENT_UPDATED");
        activity.forEach(entry -> {
            assertThat(entry.get("createdAt").asText()).isNotBlank();
            assertThat(entry.get("performedByName").asText()).isNotBlank();
            assertThat(entry.get("description").asText()).isNotBlank();
        });
    }

    // ------------------------------------------------------------- filters

    @Test
    void theQueueCanBeFilteredByStatusPriorityAndSearch() throws Exception {
        String adminToken = tokenFor("admin@test.local");
        String employeeToken = tokenFor("employee@test.local");

        String vpn = createIncident(employeeToken, "Filterable VPN outage");
        createIncident(employeeToken, "Filterable printer outage");
        resolve(adminToken, vpn, "Replaced the concentrator");

        assertThat(count(adminToken, "?status=RESOLVED&search=Filterable")).isEqualTo(1);
        assertThat(count(adminToken, "?status=NEW&search=Filterable")).isEqualTo(1);
        assertThat(count(adminToken, "?search=Filterable")).isEqualTo(2);
        assertThat(count(adminToken, "?priority=HIGH&search=Filterable")).isEqualTo(2);
        assertThat(count(adminToken, "?priority=LOW&search=Filterable")).isZero();
        // Case-insensitive substring match across the title.
        assertThat(count(adminToken, "?search=filterable vpn")).isEqualTo(1);
    }

    @Test
    void aFilterCannotWidenWhatARoleMaySee() throws Exception {
        createIncident(tokenFor("employee@test.local"), "Scoped to the reporter");

        // The other employee asks for everything; the role scope still applies.
        String body = mockMvc.perform(get("/api/incidents?search=Scoped")
                .header("Authorization", "Bearer " + tokenFor("other@test.local")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(body).get("content")).isEmpty();
    }

    @Test
    void anAgentSeesUntriagedIncidentsAndLosesThemOnceRoutedElsewhere() throws Exception {
        String agentToken = tokenFor("agent@test.local");
        String incidentId = createIncident(tokenFor("employee@test.local"), "Untriaged item");

        assertThat(count(agentToken, "?search=Untriaged")).isEqualTo(1);

        // Route it to the other team: it leaves this agent's queue entirely.
        String otherTeamId = otherTeamId();
        mockMvc.perform(patch("/api/incidents/" + incidentId + "/assign")
                .header("Authorization", "Bearer " + tokenFor("admin@test.local"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"teamId\":\"" + otherTeamId + "\"}"))
            .andExpect(status().isOk());

        assertThat(count(agentToken, "?search=Untriaged")).isZero();
        mockMvc.perform(get("/api/incidents/" + incidentId)
                .header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isForbidden());
    }

    private String otherTeamId() {
        return teamRepository.findByNameIgnoreCase("Applications").orElseThrow().getId().toString();
    }

    private int count(String token, String query) throws Exception {
        String body = mockMvc.perform(get("/api/incidents" + query)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        // GET /api/incidents returns a paged envelope as of Phase 8.
        return objectMapper.readTree(body).get("totalElements").asInt();
    }

    private void resolve(String token, String incidentId, String summary) throws Exception {
        mockMvc.perform(patch("/api/incidents/" + incidentId + "/resolve")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolutionSummary\":\"" + summary + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void employeeCommentsAreNeverInternalEvenWhenRequested() throws Exception {
        String token = tokenFor("employee@test.local");
        String incidentId = createIncident(token, "Access request");

        mockMvc.perform(post("/api/incidents/" + incidentId + "/comments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"comment":"Any update?","internal":true}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.internal").value(false));
    }

    @Test
    void anInternalAgentNoteIsHiddenFromTheReportingEmployee() throws Exception {
        String employeeToken = tokenFor("employee@test.local");
        String incidentId = createIncident(employeeToken, "Disk full");

        mockMvc.perform(post("/api/incidents/" + incidentId + "/comments")
                .header("Authorization", "Bearer " + tokenFor("admin@test.local"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"comment":"Escalating internally","internal":true}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.internal").value(true));

        String body = mockMvc.perform(get("/api/incidents/" + incidentId)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(body).get("comments")).isEmpty();
    }

    // ---------------------------------------------------------- helpers

    private String login(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    private String userId(String email) throws Exception {
        return userRepository.findByEmailIgnoreCase(email).orElseThrow().getId().toString();
    }

    private String tokenFor(String email) throws Exception {
        return objectMapper.readTree(login(email)).get("token").asText();
    }

    private String createIncident(String token, String title) throws Exception {
        String response = mockMvc.perform(post("/api/incidents")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title
                    + "\",\"description\":\"Details\",\"category\":\"Network\",\"priority\":\"HIGH\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }
}
