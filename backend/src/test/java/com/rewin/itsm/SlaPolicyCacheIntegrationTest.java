package com.rewin.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.rewin.itsm.domain.enums.Priority;
import com.rewin.itsm.domain.enums.RoleName;
import com.rewin.itsm.domain.sla.SlaPolicy;
import com.rewin.itsm.domain.user.User;
import com.rewin.itsm.repository.SlaPolicyRepository;
import com.rewin.itsm.repository.UserRepository;
import com.rewin.itsm.service.AdminService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The SLA policy list is cached, and every policy write empties the cache.
 *
 * <p>The test profile runs without a cache; this class turns an in-memory one
 * on. It exercises the same {@code @Cacheable} / {@code @CacheEvict} annotations
 * Redis serves in production - only the store differs.
 *
 * <p>Staleness is the evidence: a policy saved straight into the repository
 * bypasses the service, so it appears in the list only once something has
 * evicted the cache.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.cache.type=simple")
class SlaPolicyCacheIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String ADMIN = "cache-admin@test.local";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CacheManager cacheManager;
    @Autowired private UserRepository userRepository;
    @Autowired private SlaPolicyRepository slaPolicyRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seed() {
        cacheManager.getCache(AdminService.SLA_POLICIES_CACHE).clear();
        if (!userRepository.existsByEmailIgnoreCase(ADMIN)) {
            userRepository.save(new User(ADMIN, passwordEncoder.encode(PASSWORD), "Cache Admin", RoleName.ADMIN));
        }
    }

    @Test
    void theListIsServedFromTheCacheUntilAPolicyIsCreated() throws Exception {
        String token = adminToken();
        policyNames(token);

        slaPolicyRepository.save(new SlaPolicy("Cache bypass A", Priority.LOW, 60, 600));
        assertThat(policyNames(token)).doesNotContain("Cache bypass A");

        mockMvc.perform(post("/api/sla-policies")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Cache created","priority":"LOW","responseTimeMinutes":30,
                     "resolutionTimeMinutes":300,"active":false}"""))
            .andExpect(status().isCreated());

        assertThat(policyNames(token)).contains("Cache bypass A", "Cache created");
    }

    @Test
    void updatingAPolicyEvictsTheList() throws Exception {
        String token = adminToken();
        SlaPolicy policy = slaPolicyRepository.save(new SlaPolicy("Cache update target", Priority.MEDIUM, 30, 300));
        policyNames(token);

        slaPolicyRepository.save(new SlaPolicy("Cache bypass B", Priority.LOW, 60, 600));
        assertThat(policyNames(token)).doesNotContain("Cache bypass B");

        mockMvc.perform(put("/api/sla-policies/" + policy.getId())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Cache update target renamed","priority":"MEDIUM","responseTimeMinutes":30,
                     "resolutionTimeMinutes":300,"active":false}"""))
            .andExpect(status().isOk());

        assertThat(policyNames(token)).contains("Cache bypass B", "Cache update target renamed");
    }

    @Test
    void activatingOrDeactivatingAPolicyEvictsTheList() throws Exception {
        String token = adminToken();
        SlaPolicy policy = slaPolicyRepository.save(new SlaPolicy("Cache toggle target", Priority.LOW, 30, 300));
        policyNames(token);

        slaPolicyRepository.save(new SlaPolicy("Cache bypass C", Priority.LOW, 60, 600));
        assertThat(policyNames(token)).doesNotContain("Cache bypass C");

        mockMvc.perform(patch("/api/sla-policies/" + policy.getId() + "/active")
                .param("active", "false")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        assertThat(policyNames(token)).contains("Cache bypass C");
    }

    @Test
    void aRejectedWriteStillLeavesTheCacheConsistent() throws Exception {
        String token = adminToken();
        policyNames(token);

        // Resolution shorter than response: rejected, nothing changes.
        mockMvc.perform(post("/api/sla-policies")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Cache invalid","priority":"LOW","responseTimeMinutes":300,
                     "resolutionTimeMinutes":30,"active":false}"""))
            .andExpect(status().isBadRequest());

        assertThat(policyNames(token)).doesNotContain("Cache invalid");
    }

    private List<String> policyNames(String token) throws Exception {
        String body = mockMvc.perform(get("/api/sla-policies").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        List<String> names = new ArrayList<>();
        objectMapper.readTree(body).forEach(policy -> names.add(policy.get("name").asText()));
        return names;
    }

    private String adminToken() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + ADMIN + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }
}
