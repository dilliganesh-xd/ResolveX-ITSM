package com.rewin.itsm.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The application's source of time.
 *
 * <p>Everything SLA-related reads the clock through this bean rather than
 * calling {@code Instant.now()} directly, so a test can move time forward
 * instead of sleeping through a real deadline. UTC because deadlines are stored
 * as instants and must not shift with a server's timezone.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
