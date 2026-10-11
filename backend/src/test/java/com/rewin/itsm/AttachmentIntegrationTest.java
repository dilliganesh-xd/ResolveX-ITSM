package com.rewin.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.rewin.itsm.common.NotFoundException;
import com.rewin.itsm.domain.enums.RoleName;
import com.rewin.itsm.domain.incident.IncidentAttachment;
import com.rewin.itsm.domain.team.Team;
import com.rewin.itsm.domain.user.User;
import com.rewin.itsm.repository.IncidentAttachmentRepository;
import com.rewin.itsm.repository.TeamRepository;
import com.rewin.itsm.repository.UserRepository;
import com.rewin.itsm.storage.AttachmentStorage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Incident attachments end to end, against the local storage the test profile
 * uses in place of S3: who may upload, list, download and remove, what files
 * are accepted, and that metadata and stored file stay in step.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttachmentIntegrationTest {

    private static final String PASSWORD = "Password123!";

    /** A real PNG signature followed by some bytes. */
    private static final byte[] PNG = {
        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R', 1, 2, 3, 4};

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private IncidentAttachmentRepository attachmentRepository;
    @Autowired private AttachmentStorage storage;

    @BeforeEach
    void seed() {
        if (userRepository.existsByEmailIgnoreCase("files-admin@test.local")) {
            return;
        }
        Team team = teamRepository.save(new Team("Files Team", "Fixture"));
        save("files-admin@test.local", "Fiona Admin", RoleName.ADMIN, null);
        save("files-agent@test.local", "Felix Agent", RoleName.SUPPORT_AGENT, team);
        save("files-employee@test.local", "Fred Employee", RoleName.EMPLOYEE, null);
        save("files-other@test.local", "Flo Other", RoleName.EMPLOYEE, null);
    }

    private void save(String email, String name, RoleName role, Team team) {
        User user = new User(email, passwordEncoder.encode(PASSWORD), name, role);
        user.setTeam(team);
        userRepository.save(user);
    }

    // ------------------------------------------------------------ happy path

    @Test
    void theReporterCanAttachListAndDownloadAFile() throws Exception {
        String token = tokenFor("files-employee@test.local");
        String id = create(token, "Screen goes black");

        mockMvc.perform(multipart("/api/incidents/" + id + "/attachments")
                .file(new MockMultipartFile("file", "black screen.png", "image/png", PNG))
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.fileName").value("black screen.png"))
            .andExpect(jsonPath("$.contentType").value("image/png"))
            .andExpect(jsonPath("$.sizeBytes").value(PNG.length))
            .andExpect(jsonPath("$.uploadedByName").value("Fred Employee"))
            .andExpect(jsonPath("$.incidentId").value(id))
            // The storage key is internal and never leaves the server.
            .andExpect(jsonPath("$.objectKey").doesNotExist());

        JsonNode list = attachments(token, id);
        assertThat(list).hasSize(1);
        String attachmentId = list.get(0).get("id").asText();

        byte[] downloaded = mockMvc.perform(get(downloadUrl(id, attachmentId))
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/png"))
            .andExpect(header().string("Content-Disposition", startsWith("attachment;")))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(PNG);
    }

    @Test
    void theStorageKeyIsGeneratedAndNeverTheUploadedName() throws Exception {
        String token = tokenFor("files-employee@test.local");
        String id = create(token, "Two files with one name");

        upload(token, id, "same.png", PNG).andExpect(status().isCreated());
        upload(token, id, "same.png", PNG).andExpect(status().isCreated());

        List<String> keys = new ArrayList<>();
        for (IncidentAttachment attachment : attachmentRepository.findAllByIncidentIdOrderByCreatedAtAsc(UUID.fromString(id))) {
            keys.add(attachment.getObjectKey());
        }
        assertThat(keys).hasSize(2).doesNotHaveDuplicates()
            .allMatch(key -> key.startsWith("incidents/" + id + "/") && key.endsWith(".png"))
            .noneMatch(key -> key.contains("same"));
    }

    @Test
    void addingAnAttachmentIsRecordedInTheActivityTrail() throws Exception {
        String token = tokenFor("files-employee@test.local");
        String id = create(token, "Trail records attachments");
        upload(token, id, "evidence.png", PNG).andExpect(status().isCreated());

        String body = mockMvc.perform(get("/api/incidents/" + id + "/activities")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        List<String> descriptions = new ArrayList<>();
        objectMapper.readTree(body).get("content").forEach(entry -> {
            if ("ATTACHMENT_ADDED".equals(entry.get("action").asText())) {
                descriptions.add(entry.get("description").asText());
            }
        });
        assertThat(descriptions).containsExactly("Attachment evidence.png added by Fred Employee");
    }

    // --------------------------------------------------------- authorization

    @Test
    void anotherEmployeeCanNeitherListUploadNorDownload() throws Exception {
        String owner = tokenFor("files-employee@test.local");
        String outsider = tokenFor("files-other@test.local");
        String id = create(owner, "Private to the reporter");
        String attachmentId = idOf(upload(owner, id, "private.png", PNG));

        mockMvc.perform(get("/api/incidents/" + id + "/attachments")
                .header("Authorization", "Bearer " + outsider))
            .andExpect(status().isForbidden());
        mockMvc.perform(get(downloadUrl(id, attachmentId))
                .header("Authorization", "Bearer " + outsider))
            .andExpect(status().isForbidden());
        upload(outsider, id, "intruder.png", PNG).andExpect(status().isForbidden());

        assertThat(attachments(owner, id)).hasSize(1);
    }

    /**
     * Attachments follow the incident's own visibility rules rather than a copy
     * of them: an untriaged NEW incident sits in the shared queue every agent
     * can see, so its files are visible to every agent too.
     */
    @Test
    void anAgentSeesTheAttachmentsOfAnIncidentInTheSharedQueue() throws Exception {
        String owner = tokenFor("files-employee@test.local");
        String id = create(owner, "Shared queue with a file");
        String attachmentId = idOf(upload(owner, id, "queue.png", PNG));

        mockMvc.perform(get(downloadUrl(id, attachmentId))
                .header("Authorization", "Bearer " + tokenFor("files-agent@test.local")))
            .andExpect(status().isOk());
    }

    @Test
    void anAdminCanDownloadAnyIncidentsAttachment() throws Exception {
        String owner = tokenFor("files-employee@test.local");
        String id = create(owner, "Admin may read");
        String attachmentId = idOf(upload(owner, id, "for-admin.png", PNG));

        mockMvc.perform(get(downloadUrl(id, attachmentId))
                .header("Authorization", "Bearer " + tokenFor("files-admin@test.local")))
            .andExpect(status().isOk());
    }

    @Test
    void anAttachmentCannotBeReachedThroughAnotherIncident() throws Exception {
        String token = tokenFor("files-employee@test.local");
        String first = create(token, "Holds the file");
        String second = create(token, "Does not hold the file");
        String attachmentId = idOf(upload(token, first, "only-here.png", PNG));

        mockMvc.perform(get(downloadUrl(second, attachmentId))
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    @Test
    void anUnauthenticatedCallerIsRejected() throws Exception {
        String id = create(tokenFor("files-employee@test.local"), "Needs a login");
        mockMvc.perform(get("/api/incidents/" + id + "/attachments")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ validation

    @Test
    void aFileTypeOutsideTheAllowListIsRejected() throws Exception {
        String token = tokenFor("files-employee@test.local");
        String id = create(token, "Executable upload");

        upload(token, id, "setup.exe", new byte[] {'M', 'Z', 1, 2})
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(startsWith("Only PNG")));
        upload(token, id, "no-extension", PNG).andExpect(status().isBadRequest());
        assertThat(attachments(token, id)).isEmpty();
    }

    @Test
    void aFileWhoseContentsDoNotMatchItsExtensionIsRejected() throws Exception {
        String token = tokenFor("files-employee@test.local");
        String id = create(token, "Renamed binary");

        upload(token, id, "photo.png", "not really an image".getBytes(StandardCharsets.UTF_8))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("The file's contents do not match its .png extension."));
        upload(token, id, "notes.txt", new byte[] {'a', 0, 'b'}).andExpect(status().isBadRequest());
        assertThat(attachments(token, id)).isEmpty();
    }

    @Test
    void emptyAndOversizedFilesAreRejected() throws Exception {
        String token = tokenFor("files-employee@test.local");
        String id = create(token, "Size limits");

        upload(token, id, "empty.txt", new byte[0]).andExpect(status().isBadRequest());

        byte[] tooBig = new byte[10 * 1024 * 1024 + 1];
        upload(token, id, "huge.txt", tooBig)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Files can be at most 10 MB."));
        assertThat(attachments(token, id)).isEmpty();
    }

    @Test
    void aRequestWithoutAFileIsABadRequestNotAServerError() throws Exception {
        String token = tokenFor("files-employee@test.local");
        String id = create(token, "No file part");
        mockMvc.perform(multipart("/api/incidents/" + id + "/attachments")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest());
    }

    @Test
    void aClosedIncidentTakesNoNewAttachments() throws Exception {
        String employee = tokenFor("files-employee@test.local");
        String admin = tokenFor("files-admin@test.local");
        String id = create(employee, "Closed incidents are final");

        mockMvc.perform(patch("/api/incidents/" + id + "/resolve")
            .header("Authorization", "Bearer " + admin)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"resolutionSummary\":\"Fixed\"}")).andExpect(status().isOk());
        mockMvc.perform(patch("/api/incidents/" + id + "/close")
            .header("Authorization", "Bearer " + admin)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}")).andExpect(status().isOk());

        upload(employee, id, "late.png", PNG).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- remove

    @Test
    void onlyAnAdminCanRemoveAnAttachmentAndTheStoredFileGoesWithIt() throws Exception {
        String employee = tokenFor("files-employee@test.local");
        String id = create(employee, "Remove an attachment");
        String attachmentId = idOf(upload(employee, id, "remove-me.png", PNG));
        String key = attachmentRepository.findById(UUID.fromString(attachmentId)).orElseThrow().getObjectKey();

        mockMvc.perform(delete("/api/incidents/" + id + "/attachments/" + attachmentId)
                .header("Authorization", "Bearer " + employee))
            .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/incidents/" + id + "/attachments/" + attachmentId)
                .header("Authorization", "Bearer " + tokenFor("files-admin@test.local")))
            .andExpect(status().isNoContent());

        assertThat(attachments(employee, id)).isEmpty();
        mockMvc.perform(get(downloadUrl(id, attachmentId)).header("Authorization", "Bearer " + employee))
            .andExpect(status().isNotFound());
        assertThatThrownBy(() -> {
            try (InputStream ignored = storage.open(key)) {
                // opening is the assertion
            }
        }).isInstanceOf(NotFoundException.class);
    }

    // --------------------------------------------------------------- helpers

    private ResultActions upload(String token, String incidentId, String fileName, byte[] content)
            throws Exception {
        return mockMvc.perform(multipart("/api/incidents/" + incidentId + "/attachments")
            .file(new MockMultipartFile("file", fileName, "application/octet-stream", content))
            .header("Authorization", "Bearer " + token));
    }

    private String idOf(ResultActions created) throws Exception {
        String body = created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String downloadUrl(String incidentId, String attachmentId) {
        return "/api/incidents/" + incidentId + "/attachments/" + attachmentId + "/download";
    }

    private JsonNode attachments(String token, String incidentId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/incidents/" + incidentId + "/attachments")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
    }

    private String create(String token, String title) throws Exception {
        String body = mockMvc.perform(post("/api/incidents")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\",\"description\":\"Details\","
                    + "\"category\":\"Hardware\",\"priority\":\"LOW\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String tokenFor(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }
}
