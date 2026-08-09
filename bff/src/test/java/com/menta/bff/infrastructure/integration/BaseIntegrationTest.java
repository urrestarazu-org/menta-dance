package com.menta.bff.infrastructure.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base class for integration tests.
 * <p>
 * Provides:
 * - Singleton Testcontainers Redis (from AbstractTestcontainersConfig)
 * - Singleton WireMock for Auth API mocking (from AbstractTestcontainersConfig)
 * - MockMvc for HTTP testing
 * - Redis and WireMock cleanup between tests
 * </p>
 * <p>
 * Integration tests verify end-to-end flows with real Redis and mocked Auth API.
 * </p>
 * <p>
 * Pattern: Extends AbstractTestcontainersConfig for singleton resource management.
 * This prevents "Connection reset" errors and WireMock port mismatches when running
 * multiple test classes together.
 * </p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest extends AbstractTestcontainersConfig {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @Autowired
    protected RedisConnectionFactory redisConnectionFactory;

    /**
     * Reset WireMock stubs before each test.
     * <p>
     * Uses singleton WIRE_MOCK_SERVER from AbstractTestcontainersConfig.
     * </p>
     */
    @BeforeEach
    void resetWireMock() {
        WIRE_MOCK_SERVER.resetAll();
    }

    /**
     * Clear Redis data after each test to ensure test isolation.
     * <p>
     * IMPORTANT: This runs after EACH test method, not just after each test class.
     * </p>
     */
    @AfterEach
    void clearRedis() {
        if (redisConnectionFactory != null) {
            try (RedisConnection connection = redisConnectionFactory.getConnection()) {
                connection.serverCommands().flushAll();
            } catch (Exception e) {
                // Log error but don't fail test cleanup
                System.err.println("Failed to clear Redis after test: " + e.getMessage());
            }
        }
    }

    /**
     * Helper: Get WireMock base URL for building expected requests.
     * <p>
     * Uses singleton WIRE_MOCK_SERVER from AbstractTestcontainersConfig.
     * </p>
     */
    protected String getAuthApiBaseUrl() {
        return "http://localhost:" + WIRE_MOCK_SERVER.port();
    }
}
