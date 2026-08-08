package com.menta.bff.infrastructure.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests.
 * <p>
 * Provides:
 * - Testcontainers Redis (same version as docker-compose: redis:7.4-alpine)
 * - WireMock for Auth API mocking
 * - MockMvc for HTTP testing
 * - Redis cleanup between tests
 * </p>
 * <p>
 * Integration tests verify end-to-end flows with real Redis and mocked Auth API.
 * </p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
public abstract class BaseIntegrationTest {

    /**
     * Redis container using same image as production (docker-compose).
     */
    @Container
    protected static final GenericContainer<?> redisContainer = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine")
    )
            .withExposedPorts(6379)
            .withReuse(true);

    /**
     * WireMock server for mocking Auth API responses.
     */
    protected static WireMockServer wireMockServer;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @Autowired
    protected RedisConnectionFactory redisConnectionFactory;

    /**
     * Start WireMock server before all tests.
     */
    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(
                WireMockConfiguration.options()
                        .dynamicPort()
        );
        wireMockServer.start();
    }

    /**
     * Stop WireMock server after all tests.
     */
    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    /**
     * Reset WireMock stubs before each test.
     */
    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
    }

    /**
     * Clear Redis data after each test to ensure test isolation.
     */
    @AfterEach
    void clearRedis() {
        if (redisConnectionFactory != null) {
            try (RedisConnection connection = redisConnectionFactory.getConnection()) {
                connection.serverCommands().flushAll();
            }
        }
    }

    /**
     * Configure Spring properties dynamically from Testcontainers.
     * <p>
     * Redis connection from container.
     * Auth API base URL from WireMock.
     * </p>
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Redis configuration from Testcontainers
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));

        // Auth API base URL from WireMock
        registry.add("menta.auth.base-url", () -> "http://localhost:" + wireMockServer.port());
    }

    /**
     * Helper: Get WireMock base URL for building expected requests.
     */
    protected String getAuthApiBaseUrl() {
        return "http://localhost:" + wireMockServer.port();
    }
}
