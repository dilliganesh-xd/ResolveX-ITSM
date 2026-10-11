package com.rewin.itsm.config;

import com.rewin.itsm.domain.enums.RoleName;
import com.rewin.itsm.domain.user.User;
import com.rewin.itsm.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Creates the first administrator in an empty deployed database.
 *
 * <p>A fresh PostgreSQL database has no accounts, and accounts are only ever
 * created by an administrator, so somebody has to be first. When
 * {@code ADMIN_BOOTSTRAP_EMAIL} and {@code ADMIN_BOOTSTRAP_PASSWORD} are set,
 * this creates that one admin - and only while no admin exists at all. Once
 * any admin is present it does nothing, so leaving the variables set, or
 * changing them later, never adds or resets an account.
 *
 * <p>The values come from the environment (a Jenkins credential when deployed);
 * the password is stored BCrypt-hashed like any other.
 */
@Configuration
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    @Bean
    public ApplicationRunner bootstrapFirstAdmin(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                                 @Value("${app.bootstrap-admin.email}") String email,
                                                 @Value("${app.bootstrap-admin.password:}") String password,
                                                 @Value("${app.bootstrap-admin.name:Administrator}") String name) {
        return args -> {
            if (email.isBlank()) {
                return; // Not requested - the normal case.
            }
            if (userRepository.existsByRole(RoleName.ADMIN)) {
                log.info("An administrator already exists; first-admin bootstrap skipped.");
                return;
            }
            if (password.length() < 8) {
                log.warn("ADMIN_BOOTSTRAP_PASSWORD must be at least 8 characters; no administrator was created.");
                return;
            }
            try {
                userRepository.save(new User(email.trim().toLowerCase(), passwordEncoder.encode(password),
                    name.trim(), RoleName.ADMIN));
                log.info("Created the first administrator account: {}", email.trim().toLowerCase());
            } catch (DataIntegrityViolationException e) {
                // Another instance starting at the same moment created it first.
                log.info("The first administrator was created concurrently; nothing to do.");
            }
        };
    }
}
