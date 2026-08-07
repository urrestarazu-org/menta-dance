package com.menta.bff.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for Redis session configuration.
 * Verifies that Spring Session is properly configured with Redis backend.
 */
@SpringBootTest
class RedisSessionConfigTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }

    @Test
    void redisHttpSessionIsEnabled() {
        // Verify that RedisSessionConfig bean exists
        assertThat(context.containsBean("redisSessionConfig"))
                .as("RedisSessionConfig bean should be present")
                .isTrue();
    }

    @Test
    void cookieSerializerIsConfigured() {
        // Verify that custom cookie serializer is configured
        assertThat(context.containsBean("cookieSerializer"))
                .as("Custom cookie serializer should be configured")
                .isTrue();
    }
}
