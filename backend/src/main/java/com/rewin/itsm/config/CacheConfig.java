package com.rewin.itsm.config;

import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cache behaviour shared by every cache in the application.
 *
 * <p>The cache backend comes from {@code spring.cache.type}: Redis when deployed,
 * an in-memory map for local development and tests (see the profile files).
 *
 * <p>Only data that is identical for every caller is cached - currently the SLA
 * policy list. Incident lists and dashboard counts depend on who is asking and
 * on the clock, so they are deliberately not cached.
 */
@Configuration
public class CacheConfig implements CachingConfigurer {

    /**
     * Redis is an accelerator, not a source of truth. If it is unreachable, a
     * cache read is treated as a miss and a cache write is skipped, both logged,
     * so the request is answered from the database instead of failing with 500.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    /**
     * Evictions wait until the surrounding transaction commits. Otherwise a read
     * arriving between the eviction and the commit would load the old rows and
     * put them straight back into the cache.
     */
    @Bean
    public RedisCacheManagerBuilderCustomizer transactionAwareRedisCache() {
        return builder -> builder.transactionAware();
    }
}
