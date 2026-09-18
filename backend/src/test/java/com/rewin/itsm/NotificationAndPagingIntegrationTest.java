package com.rewin.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** Password reset, notifications, and the paged incident queue. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationAndPagingIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private SlaPolicyRepository slaPolicyRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seed() {
        if (userRepository.existsByEmailIgnoreCase("notif-admin@test.local")) {
            return;
        }
        Team team = teamRepository.save(new Team("Notify Team", "Fixture"));
        if (slaPolicyRepository.findFirstByPriorityAndActiveTrue(Priority.HIGH).isEmpty()) {
            slaPolicyRepository.save(new SlaPolicy("Notify High - 30m / 8h", Priority.HIGH, 30, 480));
        }

        save("notif-admin@test.local", "Notify Admin", RoleName.ADMIN, null);
        save("notif-agent@test.local", "Notify Agent", RoleName.SUPPORT_AGENT, team);
        save("notif-employee@test.local", "Notify Employee", RoleName.EMPLOYEE, null);
        save("notif-other@test.local", "Notify Other", RoleName.EMPLOYEE, null);
    }

    private void save(String email, String name, RoleName role, Team team) {
        User user = new User(email, passwordEncoder.encode(PASSWORD), name, role);
        user.setTeam(team);
        userRepository.save(user);
    }

    // ------------------------------------------------------ password reset

    @Test
    void anAdminResetsAPasswordAndTheNewOneWorksWhileTheOldOneDoesNot() throws Exception {
        String id = createUser("reset.target@test.local", "Reset Target");
        String oldHash = userRepository.findByEmailIgnoreCase("reset.target@test.local")
            .orElseThrow().getPasswordHash();

        String body = mockMvc.perform(post("/api/users/" + id + "/reset-password")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"password":"BrandNewPass99!"}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("reset.target@test.local"))
            .andReturn().getResponse().getContentAsString();

        // The new password is nowhere in the response.
        assertThat(body).doesNotContain("BrandNewPass99!");
        JsonNode json = objectMapper.readTree(body);
        assertThat(json.has("password")).isFalse();
        assertThat(json.has("passwordHash")).isFalse();

        // Stored hashed, and genuinely changed.
        User stored = userRepository.findByEmailIgnoreCase("reset.target@test.local").orElseThrow();
        assertThat(stored.getPasswordHash()).startsWith("$2");
        assertThat(stored.getPasswordHash()).isNotEqualTo(oldHash);
        assertThat(passwordEncoder.matches("BrandNewPass99!", stored.getPasswordHash())).isTrue();

        login("reset.target@test.local", "BrandNewPass99!");
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"reset.target@test.local\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void aShortPasswordIsRejectedAndAnUnknownUserIsANotFound() throws Exception {
        String id = createUser("reset.validation@test.local", "Reset Validation");

        mockMvc.perform(post("/api/users/" + id + "/reset-password")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"password":"short"}"""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.password").exists());

        mockMvc.perform(post("/api/users/" + java.util.UUID.randomUUID() + "/reset-password")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"password":"BrandNewPass99!"}"""))
            .andExpect(status().isNotFound());
    }

    @Test
    void neitherAnEmployeeNorAnAgentCanResetAPassword() throws Exception {
        String id = createUser("reset.protected@test.local", "Reset Protected");

        for (String token : new String[] {employeeToken(), agentToken()}) {
            mockMvc.perform(post("/api/users/" + id + "/reset-password")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"password":"TakeoverPass99!"}"""))
                .andExpect(status().isForbidden());
        }

        // The credential is untouched, so the original password still works.
        login("reset.protected@test.local", PASSWORD);
    }

    // -------------------------------------------------------- notifications

    @Test
    void anAgentIsNotifiedWhenAnIncidentIsAssignedToThem() throws Exception {
        String incidentId = createIncident(employeeToken(), "Assignment notification");
        String agentId = userRepository.findByEmailIgnoreCase("notif-agent@test.local").orElseThrow()
            .getId().toString();

        mockMvc.perform(patch("/api/incidents/" + incidentId + "/assign")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"agentId\":\"" + agentId + "\"}"))
            .andExpect(status().isOk());

        assertThat(typesFor(agentToken())).contains("INCIDENT_ASSIGNED");
        assertThat(notifications(agentToken()).get("content").get(0).get("relatedIncidentId").asText())
            .isEqualTo(incidentId);
    }

    @Test
    void theReporterIsNotifiedWhenTheirIncidentIsResolvedAndClosed() throws Exception {
        String employeeToken = employeeToken();
        String incidentId = createIncident(employeeToken, "Resolution notification");

        mockMvc.perform(patch("/api/incidents/" + incidentId + "/resolve")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"resolutionSummary":"Sorted"}"""))
            .andExpect(status().isOk());
        mockMvc.perform(patch("/api/incidents/" + incidentId + "/close")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk());

        assertThat(typesFor(employeeToken)).contains("INCIDENT_RESOLVED", "INCIDENT_CLOSED");
    }

    @Test
    void aCommentNotifiesTheOtherPartyButNeverItsOwnAuthor() throws Exception {
        String employeeToken = employeeToken();
        String incidentId = createIncident(employeeToken, "Comment notification");
        int before = countFor(employeeToken);

        // The reporter's own comment must not notify the reporter.
        comment(employeeToken, incidentId, "Any update?", false);
        assertThat(countFor(employeeToken)).isEqualTo(before);

        // An agent's reply does reach them.
        comment(adminToken(), incidentId, "Looking into it", false);
        assertThat(typesFor(employeeToken)).contains("COMMENT_ADDED");
    }

    @Test
    void anInternalNoteNeverNotifiesTheReportingEmployee() throws Exception {
        String employeeToken = employeeToken();
        String incidentId = createIncident(employeeToken, "Internal note notification");
        int before = countFor(employeeToken);

        comment(adminToken(), incidentId, "Vendor ticket 9912", true);

        assertThat(countFor(employeeToken)).isEqualTo(before);
    }

    @Test
    void aUserCannotSeeOrTouchAnotherUsersNotifications() throws Exception {
        String incidentId = createIncident(employeeToken(), "Private notification");
        mockMvc.perform(patch("/api/incidents/" + incidentId + "/resolve")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"resolutionSummary":"Done"}"""))
            .andExpect(status().isOk());

        JsonNode mine = notifications(employeeToken());
        assertThat(mine.get("content")).isNotEmpty();
        String id = mine.get("content").get(0).get("id").asText();

        // The other employee's list does not contain it...
        JsonNode theirs = notifications(tokenFor("notif-other@test.local"));
        List<String> theirIds = new ArrayList<>();
        theirs.get("content").forEach(entry -> theirIds.add(entry.get("id").asText()));
        assertThat(theirIds).doesNotContain(id);

        // ...and they cannot mark it read by guessing the id. 404, not 403:
        // they have no business knowing it exists.
        mockMvc.perform(patch("/api/notifications/" + id + "/read")
                .header("Authorization", "Bearer " + tokenFor("notif-other@test.local")))
            .andExpect(status().isNotFound());
    }

    @Test
    void markingOneReadAndMarkingAllReadBothWork() throws Exception {
        String employeeToken = employeeToken();
        String first = createIncident(employeeToken, "Read state one");
        String second = createIncident(employeeToken, "Read state two");
        for (String incidentId : new String[] {first, second}) {
            mockMvc.perform(patch("/api/incidents/" + incidentId + "/resolve")
                    .header("Authorization", "Bearer " + adminToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"resolutionSummary":"Done"}"""))
                .andExpect(status().isOk());
        }

        assertThat(unreadCount(employeeToken)).isGreaterThanOrEqualTo(2);

        String id = notifications(employeeToken).get("content").get(0).get("id").asText();
        mockMvc.perform(patch("/api/notifications/" + id + "/read")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.read").value(true));

        mockMvc.perform(patch("/api/notifications/read-all")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isOk());

        assertThat(unreadCount(employeeToken)).isZero();
    }

    @Test
    void repeatingTheSameSlaStateDoesNotNotifyTwice() throws Exception {
        String incidentId = createIncident(employeeToken(), "SLA notification once");
        String agentId = userRepository.findByEmailIgnoreCase("notif-agent@test.local").orElseThrow()
            .getId().toString();
        mockMvc.perform(patch("/api/incidents/" + incidentId + "/assign")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"agentId\":\"" + agentId + "\"}"))
            .andExpect(status().isOk());

        int before = countFor(agentToken());
        // Read the incident repeatedly: each read re-evaluates the SLA, and must
        // not produce a fresh notification every time.
        for (int pass = 0; pass < 5; pass++) {
            mockMvc.perform(get("/api/incidents/" + incidentId)
                    .header("Authorization", "Bearer " + agentToken()))
                .andExpect(status().isOk());
        }
        assertThat(countFor(agentToken())).isEqualTo(before);
    }

    @Test
    void notificationsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/notifications/read-all")).andExpect(status().isUnauthorized());
    }

    // ----------------------------------------------------------- pagination

    @Test
    void theIncidentQueueIsPagedWithConsistentTotals() throws Exception {
        String token = employeeToken();
        for (int index = 0; index < 5; index++) {
            createIncident(token, "Pageable incident " + index);
        }

        JsonNode firstPage = incidents(token, "?search=Pageable incident&page=0&size=2");
        assertThat(firstPage.get("content")).hasSize(2);
        assertThat(firstPage.get("page").asInt()).isZero();
        assertThat(firstPage.get("size").asInt()).isEqualTo(2);
        assertThat(firstPage.get("totalElements").asInt()).isEqualTo(5);
        assertThat(firstPage.get("totalPages").asInt()).isEqualTo(3);
        assertThat(firstPage.get("last").asBoolean()).isFalse();

        JsonNode lastPage = incidents(token, "?search=Pageable incident&page=2&size=2");
        assertThat(lastPage.get("content")).hasSize(1);
        assertThat(lastPage.get("last").asBoolean()).isTrue();

        // Pages do not overlap.
        String firstId = firstPage.get("content").get(0).get("id").asText();
        String lastId = lastPage.get("content").get(0).get("id").asText();
        assertThat(firstId).isNotEqualTo(lastId);
    }

    @Test
    void filtersAndPagingCombineWithoutWideningTheRoleScope() throws Exception {
        String employeeToken = employeeToken();
        createIncident(employeeToken, "Filtered and paged one");
        String resolved = createIncident(employeeToken, "Filtered and paged two");
        mockMvc.perform(patch("/api/incidents/" + resolved + "/resolve")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"resolutionSummary":"Done"}"""))
            .andExpect(status().isOk());

        assertThat(incidents(employeeToken, "?search=Filtered and paged&status=RESOLVED")
            .get("totalElements").asInt()).isEqualTo(1);
        assertThat(incidents(employeeToken, "?search=Filtered and paged&status=NEW")
            .get("totalElements").asInt()).isEqualTo(1);
        assertThat(incidents(employeeToken, "?search=Filtered and paged&priority=HIGH&size=1")
            .get("totalElements").asInt()).isEqualTo(2);
        assertThat(incidents(employeeToken, "?search=Filtered and paged&slaStatus=COMPLETED")
            .get("totalElements").asInt()).isEqualTo(1);

        // The other employee sees none of them, filtered or not.
        assertThat(incidents(tokenFor("notif-other@test.local"), "?search=Filtered and paged")
            .get("totalElements").asInt()).isZero();
    }

    @Test
    void aSummaryRowCarriesPerStageSlaVerdicts() throws Exception {
        String token = employeeToken();
        createIncident(token, "Per stage verdicts");

        JsonNode row = incidents(agentToken(), "?search=Per stage verdicts").get("content").get(0);
        assertThat(row.get("slaStatus").asText()).isEqualTo("ON_TRACK");
        assertThat(row.get("responseSlaStatus").asText()).isEqualTo("ON_TRACK");
        assertThat(row.get("resolutionSlaStatus").asText()).isEqualTo("ON_TRACK");
        assertThat(row.get("responseDeadline").asText()).isNotBlank();
        assertThat(row.get("resolutionDeadline").asText()).isNotBlank();
    }

    // ------------------------------------------------------------- helpers

    private JsonNode incidents(String token, String query) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/incidents" + query)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
    }

    private JsonNode notifications(String token) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/notifications")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
    }

    private List<String> typesFor(String token) throws Exception {
        List<String> types = new ArrayList<>();
        notifications(token).get("content").forEach(entry -> types.add(entry.get("type").asText()));
        return types;
    }

    private int countFor(String token) throws Exception {
        return notifications(token).get("totalElements").asInt();
    }

    private long unreadCount(String token) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/notifications/unread-count")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString())
            .get("unread").asLong();
    }

    private void comment(String token, String incidentId, String text, boolean internal) throws Exception {
        mockMvc.perform(post("/api/incidents/" + incidentId + "/comments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"" + text + "\",\"internal\":" + internal + "}"))
            .andExpect(status().isCreated());
    }

    private String createIncident(String token, String title) throws Exception {
        String body = mockMvc.perform(post("/api/incidents")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\",\"description\":\"Details\","
                    + "\"category\":\"Network\",\"priority\":\"HIGH\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String createUser(String email, String name) throws Exception {
        String body = mockMvc.perform(post("/api/users")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"email\":\"" + email + "\","
                    + "\"password\":\"" + PASSWORD + "\",\"role\":\"EMPLOYEE\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private String tokenFor(String email) throws Exception {
        return login(email, PASSWORD);
    }

    private String adminToken() throws Exception {
        return tokenFor("notif-admin@test.local");
    }

    private String agentToken() throws Exception {
        return tokenFor("notif-agent@test.local");
    }

    private String employeeToken() throws Exception {
        return tokenFor("notif-employee@test.local");
    }
}
