package com.rewin.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rewin.itsm.domain.enums.RoleName;
import com.rewin.itsm.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The first administrator of an empty deployed database. Runs against its own
 * in-memory database, because the shared test database already holds the
 * admins other test classes create.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:bootstrap-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "app.bootstrap-admin.email=First.Admin@Test.local",
    "app.bootstrap-admin.password=Bootstrap123!",
    "app.bootstrap-admin.name=First Admin"
})
class AdminBootstrapIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired @Qualifier("bootstrapFirstAdmin") private ApplicationRunner bootstrap;

    @Test
    void anEmptyDatabaseGetsExactlyOneAdminWhoCanSignIn() throws Exception {
        assertThat(userRepository.findAllByRole(RoleName.ADMIN))
            .singleElement()
            .satisfies(admin -> {
                assertThat(admin.getEmail()).isEqualTo("first.admin@test.local");
                assertThat(admin.getName()).isEqualTo("First Admin");
            });

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"first.admin@test.local\",\"password\":\"Bootstrap123!\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void runningAgainOnceAnAdminExistsChangesNothing() throws Exception {
        long before = userRepository.count();

        bootstrap.run(new DefaultApplicationArguments());

        assertThat(userRepository.count()).isEqualTo(before);
        assertThat(userRepository.findAllByRole(RoleName.ADMIN)).hasSize(1);
    }
}
