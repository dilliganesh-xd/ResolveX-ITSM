package com.rewin.itsm.config;

import com.rewin.itsm.domain.enums.Priority;
import com.rewin.itsm.domain.enums.RoleName;
import com.rewin.itsm.domain.sla.SlaPolicy;
import com.rewin.itsm.domain.team.Team;
import com.rewin.itsm.domain.user.User;
import com.rewin.itsm.repository.SlaPolicyRepository;
import com.rewin.itsm.repository.TeamRepository;
import com.rewin.itsm.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Development seed data.
 *
 * <p>Restricted to the dev and local profiles, so it can never run in staging or
 * production. It is idempotent: if the admin account already exists, nothing is
 * written, so restarting the app does not duplicate rows or reset passwords.
 *
 * <p>The demo password is a well-known development credential and is documented
 * in the README. It is stored BCrypt-hashed like any other password; the plain
 * value never reaches the database.
 */
@Configuration
@Profile({"dev", "local"})
public class DevDataSeeder {

    /** Documented development password. Change it before any shared deployment. */
    public static final String DEMO_PASSWORD = "Password123!";

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    @Bean
    public ApplicationRunner seedDevelopmentData(UserRepository userRepository, TeamRepository teamRepository,
                                                 SlaPolicyRepository slaPolicyRepository,
                                                 PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.existsByEmailIgnoreCase("admin@itsm.local")) {
                log.info("Development seed data already present; skipping.");
                return;
            }

            Team infrastructure = teamRepository.save(
                new Team("Infrastructure", "Network, servers and access management"));
            teamRepository.save(new Team("Applications", "Business applications and integrations"));

            userRepository.save(buildUser(passwordEncoder, "admin@itsm.local", "Priya Admin", RoleName.ADMIN, null));
            userRepository.save(buildUser(passwordEncoder, "agent@itsm.local", "Sam Agent",
                RoleName.SUPPORT_AGENT, infrastructure));
            userRepository.save(buildUser(passwordEncoder, "employee@itsm.local", "Alex Employee",
                RoleName.EMPLOYEE, null));

            // One active policy per priority - response minutes, then resolution
            // minutes. SlaService selects by priority alone.
            slaPolicyRepository.save(new SlaPolicy("Critical - 15m / 4h", Priority.CRITICAL, 15, 240));
            slaPolicyRepository.save(new SlaPolicy("High - 30m / 8h", Priority.HIGH, 30, 480));
            slaPolicyRepository.save(new SlaPolicy("Medium - 1h / 24h", Priority.MEDIUM, 60, 1440));
            slaPolicyRepository.save(new SlaPolicy("Low - 4h / 72h", Priority.LOW, 240, 4320));

            log.info("Seeded development data: 3 users, 2 teams, 4 SLA policies. Password: {}", DEMO_PASSWORD);
        };
    }

    private User buildUser(PasswordEncoder encoder, String email, String name, RoleName role, Team team) {
        User user = new User(email, encoder.encode(DEMO_PASSWORD), name, role);
        user.setTeam(team);
        return user;
    }
}
