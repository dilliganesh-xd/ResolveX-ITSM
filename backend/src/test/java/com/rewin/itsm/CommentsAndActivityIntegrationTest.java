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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Comments, the audit trail, and who is allowed to read either. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommentsAndActivityIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private SlaPolicyRepository slaPolicyRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seed() {
        if (userRepository.existsByEmailIgnoreCase("audit-admin@test.local")) {
            return;
        }
        Team team = teamRepository.save(new Team("Audit Team", "Fixture"));
        teamRepository.save(new Team("Audit Team Two", "Fixture"));
        // Both priorities this class exercises need a live policy: changing an
        // incident to a priority with no active policy correctly strips its SLA,
        // which would quietly remove the SLA activities from the trail.
        slaPolicyRepository.save(new SlaPolicy("Audit High - 30m / 8h", Priority.HIGH, 30, 480));
        slaPolicyRepository.save(new SlaPolicy("Audit Critical - 15m / 4h", Priority.CRITICAL, 15, 240));

        save("audit-admin@test.local", "Priya Admin", RoleName.ADMIN, null);
        save("audit-agent@test.local", "Sarah Agent", RoleName.SUPPORT_AGENT, team);
        save("audit-agent2@test.local", "Sam Agent", RoleName.SUPPORT_AGENT, team);
        save("audit-employee@test.local", "John Employee", RoleName.EMPLOYEE, null);
        save("audit-other@test.local", "Other Employee", RoleName.EMPLOYEE, null);
    }

    private void save(String email, String name, RoleName role, Team team) {
        User user = new User(email, passwordEncoder.encode(PASSWORD), name, role);
        user.setTeam(team);
        userRepository.save(user);
    }

    // ------------------------------------------------------------ comments

    @Test
    void anEmployeeCanCommentOnTheirOwnIncidentAndReadItBack() throws Exception {
        String token = employeeToken();
        String id = create(token, "Employee comments on their own");

        String created = mockMvc.perform(post("/api/incidents/" + id + "/comments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"comment":"This is still happening"}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.comment").value("This is still happening"))
            .andExpect(jsonPath("$.authorName").value("John Employee"))
            .andExpect(jsonPath("$.authorRole").value("EMPLOYEE"))
            .andExpect(jsonPath("$.incidentId").value(id))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.updatedAt").exists())
            .andReturn().getResponse().getContentAsString();

        // Nothing about the account beyond id, name and role.
        JsonNode body = objectMapper.readTree(created);
        assertThat(body.has("password")).isFalse();
        assertThat(body.has("passwordHash")).isFalse();
        assertThat(body.has("author")).isFalse();

        JsonNode page = comments(token, id);
        assertThat(page.get("content")).hasSize(1);
        assertThat(page.get("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    void anEmployeeCannotCommentOnAnotherEmployeesIncident() throws Exception {
        String id = create(employeeToken(), "Not yours to comment on");

        mockMvc.perform(post("/api/incidents/" + id + "/comments")
                .header("Authorization", "Bearer " + tokenFor("audit-other@test.local"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"comment":"Let me in"}"""))
            .andExpect(status().isForbidden());
    }

    @Test
    void anAgentAndAnAdminCanBothComment() throws Exception {
        String id = create(employeeToken(), "Support side comments");

        mockMvc.perform(post("/api/incidents/" + id + "/comments")
                .header("Authorization", "Bearer " + agentToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"comment":"Looking into this now"}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.authorRole").value("SUPPORT_AGENT"));

        mockMvc.perform(post("/api/incidents/" + id + "/comments")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"comment":"Escalated to the vendor"}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.authorRole").value("ADMIN"));
    }

    @Test
    void anEmptyOrWhitespaceOnlyCommentIsRejected() throws Exception {
        String token = employeeToken();
        String id = create(token, "Blank comments");

        for (String candidate : new String[] {"\"\"", "\"   \"", "\"\\n\\t \""}) {
            mockMvc.perform(post("/api/incidents/" + id + "/comments")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"comment\":" + candidate + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.comment").value("Comment cannot be empty"));
        }

        assertThat(comments(token, id).get("content")).isEmpty();
    }

    @Test
    void anInternalNoteIsExcludedFromTheEmployeeCommentsEndpoint() throws Exception {
        String employeeToken = employeeToken();
        String id = create(employeeToken, "Internal note visibility");

        mockMvc.perform(post("/api/incidents/" + id + "/comments")
                .header("Authorization", "Bearer " + agentToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"comment":"Vendor ticket 4471","internal":true}"""))
            .andExpect(status().isCreated());

        assertThat(comments(employeeToken, id).get("content")).isEmpty();
        assertThat(comments(agentToken(), id).get("content")).hasSize(1);
    }

    @Test
    void theCommentsEndpointIsRefusedToAnUnauthorisedEmployee() throws Exception {
        String id = create(employeeToken(), "Comments are scoped too");

        mockMvc.perform(get("/api/incidents/" + id + "/comments")
                .header("Authorization", "Bearer " + tokenFor("audit-other@test.local")))
            .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------- activities

    @Test
    void everyLifecycleOperationIsRecordedWithAReadableDescription() throws Exception {
        String adminToken = adminToken();
        String employeeToken = employeeToken();
        String id = create(employeeToken, "Full audit trail");

        String firstAgent = userId("audit-agent@test.local");
        String secondAgent = userId("audit-agent2@test.local");
        String otherTeam = teamRepository.findByNameIgnoreCase("Audit Team Two").orElseThrow().getId().toString();

        assign(adminToken, id, "{\"agentId\":\"" + firstAgent + "\"}");
        assign(adminToken, id, "{\"agentId\":\"" + secondAgent + "\"}");
        assign(adminToken, id, "{\"teamId\":\"" + otherTeam + "\"}");

        mockMvc.perform(put("/api/incidents/" + id)
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"priority":"CRITICAL","category":"Database"}""")).andExpect(status().isOk());

        mockMvc.perform(patch("/api/incidents/" + id + "/status")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"status":"IN_PROGRESS"}""")).andExpect(status().isOk());

        mockMvc.perform(post("/api/incidents/" + id + "/comments")
            .header("Authorization", "Bearer " + employeeToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"comment":"Any update?"}""")).andExpect(status().isCreated());

        mockMvc.perform(patch("/api/incidents/" + id + "/resolve")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"resolutionSummary":"Rebuilt the index"}""")).andExpect(status().isOk());

        mockMvc.perform(patch("/api/incidents/" + id + "/close")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}")).andExpect(status().isOk());

        JsonNode page = activities(adminToken, id);
        List<String> actions = new ArrayList<>();
        List<String> descriptions = new ArrayList<>();
        page.get("content").forEach(entry -> {
            actions.add(entry.get("action").asText());
            descriptions.add(entry.get("description").asText());
        });

        assertThat(actions).contains(
            "INCIDENT_CREATED", "INCIDENT_ASSIGNED", "INCIDENT_REASSIGNED", "AGENT_CHANGED",
            "TEAM_CHANGED", "STATUS_CHANGED", "PRIORITY_CHANGED", "INCIDENT_UPDATED",
            "COMMENT_ADDED", "INCIDENT_RESOLVED", "INCIDENT_CLOSED", "SLA_COMPLETED");

        // Descriptions name the real people and values, never a placeholder.
        assertThat(descriptions).contains(
            "Incident created by John Employee",
            "Incident assigned to Sarah Agent by Priya Admin",
            "Incident reassigned from Sarah Agent to Sam Agent by Priya Admin",
            "Team changed from Audit Team to Audit Team Two by Priya Admin",
            "Priority changed from HIGH to CRITICAL by Priya Admin",
            "Category changed from Network to Database by Priya Admin",
            "Comment added by John Employee",
            "Incident closed by Priya Admin");
        assertThat(descriptions).anyMatch(d -> d.startsWith("Incident resolved by Priya Admin"));
        assertThat(descriptions).noneMatch(String::isBlank);
    }

    @Test
    void activitiesAreReturnedNewestFirstAndCarryTheirActor() throws Exception {
        String id = create(employeeToken(), "Ordering check");
        assign(adminToken(), id, "{\"agentId\":\"" + userId("audit-agent@test.local") + "\"}");

        JsonNode content = activities(adminToken(), id).get("content");
        assertThat(content.get(0).get("createdAt").asText())
            .isGreaterThanOrEqualTo(content.get(content.size() - 1).get("createdAt").asText());
        assertThat(content.get(content.size() - 1).get("action").asText()).isEqualTo("INCIDENT_CREATED");
        assertThat(content.get(content.size() - 1).get("performedByName").asText()).isEqualTo("John Employee");
        assertThat(content.get(content.size() - 1).get("performedByRole").asText()).isEqualTo("EMPLOYEE");
    }

    @Test
    void theActivitiesEndpointIsPagedAndReadableByTheReportingEmployee() throws Exception {
        String token = employeeToken();
        String id = create(token, "Employee reads their own history");

        JsonNode page = activities(token, id);
        assertThat(page.get("page").asInt()).isZero();
        assertThat(page.get("totalElements").asInt()).isPositive();
        assertThat(page.get("last").asBoolean()).isTrue();
        assertThat(page.get("content").get(0).get("description").asText())
            .isEqualTo("Incident created by John Employee");
    }

    @Test
    void theActivitiesEndpointIsRefusedToAnUnauthorisedEmployee() throws Exception {
        String id = create(employeeToken(), "History is scoped too");

        mockMvc.perform(get("/api/incidents/" + id + "/activities")
                .header("Authorization", "Bearer " + tokenFor("audit-other@test.local")))
            .andExpect(status().isForbidden());
    }

    @Test
    void anUnknownIncidentIsA404AndAMalformedIdIsA400() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/incidents/" + java.util.UUID.randomUUID() + "/activities")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Incident not found"));

        mockMvc.perform(get("/api/incidents/not-a-uuid/comments")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest());
    }

    @Test
    void theAuditTrailHasNoWriteOrDeleteEndpoint() throws Exception {
        String id = create(employeeToken(), "Trail is immutable");
        String activityId = activities(adminToken(), id).get("content").get(0).get("id").asText();

        // Neither path exists; an admin token does not conjure one into being.
        mockMvc.perform(patch("/api/incidents/" + id + "/activities/" + activityId)
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"description":"rewritten"}"""))
            .andExpect(status().isNotFound());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/incidents/" + id + "/activities/" + activityId)
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------- helpers

    private void assign(String token, String id, String payload) throws Exception {
        mockMvc.perform(patch("/api/incidents/" + id + "/assign")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk());
    }

    private JsonNode comments(String token, String id) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/incidents/" + id + "/comments")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
    }

    private JsonNode activities(String token, String id) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/incidents/" + id + "/activities")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
    }

    private String create(String token, String title) throws Exception {
        String body = mockMvc.perform(post("/api/incidents")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\",\"description\":\"Details\","
                    + "\"category\":\"Network\",\"priority\":\"HIGH\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String userId(String email) throws Exception {
        return userRepository.findByEmailIgnoreCase(email).orElseThrow().getId().toString();
    }

    private String tokenFor(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private String adminToken() throws Exception {
        return tokenFor("audit-admin@test.local");
    }

    private String agentToken() throws Exception {
        return tokenFor("audit-agent@test.local");
    }

    private String employeeToken() throws Exception {
        return tokenFor("audit-employee@test.local");
    }
}
