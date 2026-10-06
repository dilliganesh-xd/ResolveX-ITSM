package com.rewin.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Admin management of users, teams and SLA policies, and who may do it. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminManagementIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private SlaPolicyRepository slaPolicyRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seed() {
        if (userRepository.existsByEmailIgnoreCase("mgmt-admin@test.local")) {
            return;
        }
        Team team = teamRepository.save(new Team("Management Team", "Fixture"));
        slaPolicyRepository.save(new SlaPolicy("Mgmt Medium - 1h / 24h", Priority.MEDIUM, 60, 1440));

        save("mgmt-admin@test.local", "Mgmt Admin", RoleName.ADMIN, null);
        save("mgmt-agent@test.local", "Mgmt Agent", RoleName.SUPPORT_AGENT, team);
        save("mgmt-employee@test.local", "Mgmt Employee", RoleName.EMPLOYEE, null);
    }

    private void save(String email, String name, RoleName role, Team team) {
        User user = new User(email, passwordEncoder.encode(PASSWORD), name, role);
        user.setTeam(team);
        userRepository.save(user);
    }

    // ------------------------------------------------------------- users

    @Test
    void anAdminCreatesAUserWhosePasswordIsHashedAndNeverReturned() throws Exception {
        String created = mockMvc.perform(post("/api/users")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Created Person","email":"created.person@test.local",
                     "password":"Password123!","role":"SUPPORT_AGENT"}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("created.person@test.local"))
            .andExpect(jsonPath("$.role").value("SUPPORT_AGENT"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.createdAt").exists())
            .andReturn().getResponse().getContentAsString();

        JsonNode body = objectMapper.readTree(created);
        assertThat(body.has("password")).isFalse();
        assertThat(body.has("passwordHash")).isFalse();
        assertThat(created).doesNotContain(PASSWORD);

        // Stored as a BCrypt digest, not as the plain password.
        User stored = userRepository.findByEmailIgnoreCase("created.person@test.local").orElseThrow();
        assertThat(stored.getPasswordHash()).startsWith("$2");
        assertThat(stored.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, stored.getPasswordHash())).isTrue();

        // And the new account can actually sign in.
        login("created.person@test.local");
    }

    @Test
    void aDuplicateEmailIsRejectedWithAConflict() throws Exception {
        mockMvc.perform(post("/api/users")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Clash","email":"mgmt-agent@test.local",
                     "password":"Password123!","role":"EMPLOYEE"}"""))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("A user with that email already exists."));
    }

    @Test
    void anInvalidUserPayloadIsRejectedWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/users")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"","email":"not-an-email","password":"short","role":null}"""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.name").exists())
            .andExpect(jsonPath("$.fieldErrors.email").exists())
            .andExpect(jsonPath("$.fieldErrors.password").exists())
            .andExpect(jsonPath("$.fieldErrors.role").exists());
    }

    @Test
    void anUnknownTeamOnCreateIsANotFound() throws Exception {
        mockMvc.perform(post("/api/users")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ghost Team\",\"email\":\"ghost.team@test.local\","
                    + "\"password\":\"Password123!\",\"role\":\"EMPLOYEE\",\"teamId\":\""
                    + java.util.UUID.randomUUID() + "\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void anAdminUpdatesAUserWithoutTouchingTheirPassword() throws Exception {
        String id = createUser("update.me@test.local", "Update Me", "EMPLOYEE");
        String hashBefore = userRepository.findByEmailIgnoreCase("update.me@test.local")
            .orElseThrow().getPasswordHash();
        String teamId = teamRepository.findByNameIgnoreCase("Management Team").orElseThrow().getId().toString();

        mockMvc.perform(put("/api/users/" + id)
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Updated Name\",\"email\":\"updated.me@test.local\","
                    + "\"role\":\"SUPPORT_AGENT\",\"teamId\":\"" + teamId + "\",\"active\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Updated Name"))
            .andExpect(jsonPath("$.email").value("updated.me@test.local"))
            .andExpect(jsonPath("$.role").value("SUPPORT_AGENT"))
            .andExpect(jsonPath("$.team.name").value("Management Team"));

        // The credential is untouched: the old password still signs in.
        User after = userRepository.findByEmailIgnoreCase("updated.me@test.local").orElseThrow();
        assertThat(after.getPasswordHash()).isEqualTo(hashBefore);
        login("updated.me@test.local");
    }

    @Test
    void anAdminCannotDemoteOrDeactivateThemselves() throws Exception {
        String selfId = userRepository.findByEmailIgnoreCase("mgmt-admin@test.local").orElseThrow()
            .getId().toString();

        mockMvc.perform(put("/api/users/" + selfId)
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Mgmt Admin","email":"mgmt-admin@test.local","role":"EMPLOYEE","active":true}"""))
            .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/users/" + selfId + "/active?active=false")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void aDeactivatedUserCannotLogInButKeepsTheirHistory() throws Exception {
        String id = createUser("deactivate.me@test.local", "Deactivate Me", "SUPPORT_AGENT");
        login("deactivate.me@test.local");

        mockMvc.perform(patch("/api/users/" + id + "/active?active=false")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"deactivate.me@test.local\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isUnauthorized());

        // The row is still there - deactivation is not deletion.
        assertThat(userRepository.findByEmailIgnoreCase("deactivate.me@test.local")).isPresent();
    }

    @Test
    void aDeactivatedAgentIsNotOfferedForAssignmentAndCannotBeAssigned() throws Exception {
        String id = createUser("offline.agent@test.local", "Offline Agent", "SUPPORT_AGENT");
        assertThat(agentEmails()).contains("offline.agent@test.local");

        mockMvc.perform(patch("/api/users/" + id + "/active?active=false")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk());

        assertThat(agentEmails()).doesNotContain("offline.agent@test.local");

        String incidentId = createIncident();
        mockMvc.perform(patch("/api/incidents/" + incidentId + "/assign")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"agentId\":\"" + id + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void theUserListIsPagedAndFilterable() throws Exception {
        createUser("filter.one@test.local", "Filterable One", "EMPLOYEE");
        createUser("filter.two@test.local", "Filterable Two", "SUPPORT_AGENT");

        JsonNode page = users("?search=Filterable");
        assertThat(page.get("totalElements").asInt()).isEqualTo(2);
        assertThat(page.get("page").asInt()).isZero();
        assertThat(page.get("size").asInt()).isPositive();

        assertThat(users("?search=Filterable&role=EMPLOYEE").get("totalElements").asInt()).isEqualTo(1);
        assertThat(users("?search=Filterable&active=true").get("totalElements").asInt()).isEqualTo(2);
        assertThat(users("?search=Filterable&active=false").get("totalElements").asInt()).isZero();
        assertThat(users("?search=filterable one").get("totalElements").asInt()).isEqualTo(1);

        String teamId = teamRepository.findByNameIgnoreCase("Management Team").orElseThrow().getId().toString();
        assertThat(users("?teamId=" + teamId).get("totalElements").asInt()).isPositive();

        // Paging actually pages.
        JsonNode firstPage = users("?search=Filterable&page=0&size=1");
        assertThat(firstPage.get("content")).hasSize(1);
        assertThat(firstPage.get("totalPages").asInt()).isEqualTo(2);
        assertThat(firstPage.get("last").asBoolean()).isFalse();
    }

    @Test
    void theUserDetailViewCarriesWorkloadCounts() throws Exception {
        String id = userRepository.findByEmailIgnoreCase("mgmt-agent@test.local").orElseThrow()
            .getId().toString();

        mockMvc.perform(get("/api/users/" + id).header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Mgmt Agent"))
            .andExpect(jsonPath("$.assignedIncidentCount").exists())
            .andExpect(jsonPath("$.resolvedIncidentCount").exists())
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.password").doesNotExist());
    }

    // ------------------------------------------------------------- teams

    @Test
    void anAdminCreatesUpdatesAndDeactivatesATeam() throws Exception {
        String created = mockMvc.perform(post("/api/teams")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Networking","description":"Switches and routers"}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.memberCount").value(0))
            .andExpect(jsonPath("$.active").value(true))
            .andReturn().getResponse().getContentAsString();
        String teamId = objectMapper.readTree(created).get("id").asText();

        mockMvc.perform(put("/api/teams/" + teamId)
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Networking and Voice","description":"Switches, routers and telephony","active":true}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Networking and Voice"));

        mockMvc.perform(patch("/api/teams/" + teamId + "/active?active=false")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        // Deactivated, never removed.
        assertThat(teamRepository.findById(java.util.UUID.fromString(teamId))).isPresent();
    }

    @Test
    void aDuplicateTeamNameIsRejected() throws Exception {
        mockMvc.perform(post("/api/teams")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Management Team","description":"Clash"}"""))
            .andExpect(status().isConflict());
    }

    @Test
    void aTeamListsItsMembersAndCountsThem() throws Exception {
        String teamId = teamRepository.findByNameIgnoreCase("Management Team").orElseThrow().getId().toString();

        String body = mockMvc.perform(get("/api/teams/" + teamId + "/members")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        JsonNode members = objectMapper.readTree(body);
        assertThat(members).isNotEmpty();
        members.forEach(member -> {
            assertThat(member.get("name").asText()).isNotBlank();
            assertThat(member.get("role").asText()).isNotBlank();
            assertThat(member.has("password")).isFalse();
        });

        JsonNode teams = objectMapper.readTree(mockMvc.perform(get("/api/teams")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        JsonNode management = findByName(teams, "Management Team");
        assertThat(management.get("memberCount").asInt()).isEqualTo(members.size());
    }

    @Test
    void thereIsNoDeleteEndpointForATeamOrAUser() throws Exception {
        String teamId = teamRepository.findByNameIgnoreCase("Management Team").orElseThrow().getId().toString();
        String userId = userRepository.findByEmailIgnoreCase("mgmt-agent@test.local").orElseThrow()
            .getId().toString();

        // 405, not 404: the paths exist for GET and PUT, so the absence being
        // proved here is the verb, not the resource. Either way nothing deletes.
        mockMvc.perform(delete("/api/teams/" + teamId).header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/users/" + userId).header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isMethodNotAllowed());

        assertThat(teamRepository.findById(java.util.UUID.fromString(teamId))).isPresent();
        assertThat(userRepository.findById(java.util.UUID.fromString(userId))).isPresent();
    }

    // ------------------------------------------------------- sla policies

    @Test
    void anAdminCreatesAndEditsAPolicyAndActivationStandsTheOtherDown() throws Exception {
        String created = mockMvc.perform(post("/api/sla-policies")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Mgmt Medium - express","priority":"MEDIUM","responseTimeMinutes":20,
                     "resolutionTimeMinutes":120,"active":true}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.updatedAt").exists())
            .andReturn().getResponse().getContentAsString();
        String policyId = objectMapper.readTree(created).get("id").asText();

        assertThat(slaPolicyRepository.findAllByPriorityAndActiveTrue(Priority.MEDIUM)).hasSize(1);

        mockMvc.perform(put("/api/sla-policies/" + policyId)
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Mgmt Medium - express v2","priority":"MEDIUM","responseTimeMinutes":25,
                     "resolutionTimeMinutes":150,"active":true}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.responseTimeMinutes").value(25));
    }

    @Test
    void policyTimesMustBePositiveAndOrdered() throws Exception {
        for (String payload : new String[] {
            "{\"name\":\"Bad\",\"priority\":\"LOW\",\"responseTimeMinutes\":0,"
                + "\"resolutionTimeMinutes\":100,\"active\":false}",
            "{\"name\":\"Bad\",\"priority\":\"LOW\",\"responseTimeMinutes\":10,"
                + "\"resolutionTimeMinutes\":0,\"active\":false}",
            "{\"name\":\"Bad\",\"priority\":\"LOW\",\"responseTimeMinutes\":500,"
                + "\"resolutionTimeMinutes\":100,\"active\":false}"
        }) {
            mockMvc.perform(post("/api/sla-policies")
                    .header("Authorization", "Bearer " + adminToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                .andExpect(status().isBadRequest());
        }
    }

    @Test
    void editingAPolicyDoesNotMoveAnExistingIncidentDeadline() throws Exception {
        String incidentId = createIncident();
        JsonNode before = incident(incidentId);
        String deadlineBefore = before.get("resolutionDeadline").asText();
        String policyBefore = before.get("slaPolicyName").asText();

        // Rewrite the policy this incident was stamped from.
        JsonNode policies = objectMapper.readTree(mockMvc.perform(get("/api/sla-policies")
                .header("Authorization", "Bearer " + adminToken()))
            .andReturn().getResponse().getContentAsString());
        String policyId = findByName(policies, policyBefore).get("id").asText();

        mockMvc.perform(put("/api/sla-policies/" + policyId)
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + policyBefore + "\",\"priority\":\"MEDIUM\","
                    + "\"responseTimeMinutes\":5,\"resolutionTimeMinutes\":10,\"active\":true}"))
            .andExpect(status().isOk());

        // The incident keeps the commitment it was created under.
        JsonNode after = incident(incidentId);
        assertThat(after.get("resolutionDeadline").asText()).isEqualTo(deadlineBefore);
    }

    // ---------------------------------------------------------- dashboard

    @Test
    void theDashboardReportsConsistentFigures() throws Exception {
        createIncident();

        JsonNode report = objectMapper.readTree(mockMvc.perform(get("/api/reports")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());

        long byStatusTotal = 0;
        for (JsonNode count : report.get("byStatus")) {
            byStatusTotal += count.asLong();
        }
        assertThat(byStatusTotal).isEqualTo(report.get("totalIncidents").asLong());

        assertThat(report.get("totalUsers").asLong()).isGreaterThanOrEqualTo(report.get("activeUsers").asLong());
        assertThat(report.get("totalTeams").asLong()).isPositive();
        assertThat(report.get("openIncidents").asLong()).isPositive();
        assertThat(report.get("byTeam").has("Unrouted")).isTrue();
        assertThat(report.has("inProgressIncidents")).isTrue();
        assertThat(report.has("atRiskIncidents")).isTrue();
        assertThat(report.has("breachedIncidents")).isTrue();
    }

    @Test
    void administrativeActionsAreRecordedInTheAdminAudit() throws Exception {
        createUser("audited.person@test.local", "Audited Person", "EMPLOYEE");

        JsonNode page = objectMapper.readTree(mockMvc.perform(get("/api/admin/audit")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());

        assertThat(page.get("content")).isNotEmpty();
        boolean recorded = false;
        for (JsonNode entry : page.get("content")) {
            if (entry.get("description").asText().contains("Audited Person")) {
                recorded = true;
                assertThat(entry.get("targetType").asText()).isEqualTo("USER");
                assertThat(entry.get("performedByName").asText()).isEqualTo("Mgmt Admin");
            }
        }
        assertThat(recorded).isTrue();
    }

    // ---------------------------------------------------- RBAC on the API

    @Test
    void neitherAnEmployeeNorAnAgentCanReachAnyAdminEndpoint() throws Exception {
        String teamId = teamRepository.findByNameIgnoreCase("Management Team").orElseThrow().getId().toString();

        for (String token : new String[] {employeeToken(), agentToken()}) {
            mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/reports").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/admin/audit").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/teams/" + teamId + "/members").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

            mockMvc.perform(post("/api/users")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"name":"Sneaky","email":"sneaky@test.local","password":"Password123!","role":"ADMIN"}"""))
                .andExpect(status().isForbidden());

            mockMvc.perform(post("/api/teams")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"name":"Sneaky Team","description":"No"}"""))
                .andExpect(status().isForbidden());

            mockMvc.perform(put("/api/teams/" + teamId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"name":"Renamed","description":"No","active":true}"""))
                .andExpect(status().isForbidden());

            mockMvc.perform(patch("/api/teams/" + teamId + "/active?active=false")
                    .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

            mockMvc.perform(post("/api/sla-policies")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"name":"Sneaky","priority":"LOW","responseTimeMinutes":5,
                         "resolutionTimeMinutes":50,"active":true}"""))
                .andExpect(status().isForbidden());
        }

        // And the team was not renamed or deactivated by any of that.
        Team team = teamRepository.findByNameIgnoreCase("Management Team").orElseThrow();
        assertThat(team.isActive()).isTrue();
    }

    @Test
    void anonymousAdminCallsAreUnauthorisedRatherThanForbidden() throws Exception {
        mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/audit")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ helpers

    private JsonNode findByName(JsonNode array, String name) {
        for (JsonNode node : array) {
            if (node.get("name").asText().equals(name)) {
                return node;
            }
        }
        throw new AssertionError("No element named " + name);
    }

    private java.util.List<String> agentEmails() throws Exception {
        JsonNode agents = objectMapper.readTree(mockMvc.perform(get("/api/agents")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        java.util.List<String> emails = new java.util.ArrayList<>();
        agents.forEach(agent -> emails.add(agent.get("email").asText()));
        return emails;
    }

    private JsonNode users(String query) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/users" + query)
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
    }

    private JsonNode incident(String id) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/incidents/" + id)
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
    }

    private String createUser(String email, String name, String role) throws Exception {
        String body = mockMvc.perform(post("/api/users")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"email\":\"" + email + "\","
                    + "\"password\":\"" + PASSWORD + "\",\"role\":\"" + role + "\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String createIncident() throws Exception {
        String body = mockMvc.perform(post("/api/incidents")
                .header("Authorization", "Bearer " + employeeToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"Admin fixture incident","description":"Details",
                     "category":"Network","priority":"MEDIUM"}"""))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    @Test
    void anEndpointMissingItsRequiredParameterIsABadRequestNotAServerError() throws Exception {
        String id = userRepository.findByEmailIgnoreCase("mgmt-agent@test.local")
            .orElseThrow().getId().toString();

        // ?active= is absent. That is the caller's mistake, and reporting it as a
        // 500 both misleads the client and logs a stack trace for a valid refusal.
        mockMvc.perform(patch("/api/users/" + id + "/active")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Malformed request"));
    }

    private String adminToken() throws Exception {
        return login("mgmt-admin@test.local");
    }

    private String agentToken() throws Exception {
        return login("mgmt-agent@test.local");
    }

    private String employeeToken() throws Exception {
        return login("mgmt-employee@test.local");
    }
}
