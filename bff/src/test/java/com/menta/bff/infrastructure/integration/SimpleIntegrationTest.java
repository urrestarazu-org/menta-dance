package com.menta.bff.infrastructure.integration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple test to verify integration test infrastructure works.
 */
class SimpleIntegrationTest extends BaseIntegrationTest {

    @Test
    void contextLoads() {
        assertThat(mockMvc).isNotNull();
        assertThat(redisTemplate).isNotNull();
        assertThat(WIRE_MOCK_SERVER).isNotNull();
        assertThat(WIRE_MOCK_SERVER.isRunning()).isTrue();
    }

    @Test
    void redisIsConnected() {
        redisTemplate.opsForValue().set("test-key", "test-value");
        String value = redisTemplate.opsForValue().get("test-key");
        assertThat(value).isEqualTo("test-value");
    }
}
