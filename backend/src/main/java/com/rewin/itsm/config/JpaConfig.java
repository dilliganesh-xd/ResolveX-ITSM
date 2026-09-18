package com.rewin.itsm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Turns on @CreatedDate / @LastModifiedDate population for BaseEntity. */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
