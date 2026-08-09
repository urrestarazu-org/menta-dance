package com.menta.bff.infrastructure.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton Testcontainers and WireMock configuration shared across all integration test classes.
 * <p>
 * This abstract class ensures:
 * - Redis container starts ONCE before any test class
 * - WireMock server starts ONCE before any test class
 * - Both are reused across all test classes (JUnit 5 shared state)
 * - @DynamicPropertySource registers properties once with stable port mapping
 * - No "Connection reset" errors due to port remapping between test classes
 * </p>
 * <p>
 * Pattern: Singleton Container + Singleton WireMock
 * Reference: https://java.testcontainers.org/test_framework_integration/junit_5/#singleton-containers
 * </p>
 */
public abstract class AbstractTestcontainersConfig {

    /**
     * Singleton Redis container shared across ALL integration test classes.
     * <p>
     * Lifecycle:
     * - Starts before first test class
     * - Reused by all test classes in JVM
     * - Stopped when JVM exits (Testcontainers Ryuk cleanup)
     * </p>
     * <p>
     * Using same image as production (docker-compose: redis:7.4-alpine)
     * </p>
     */
    protected static final GenericContainer<?> REDIS_CONTAINER;

    /**
     * Singleton WireMock server shared across ALL integration test classes.
     * <p>
     * Lifecycle:
     * - Starts before first test class
     * - Reused by all test classes in JVM
     * - Stopped when JVM exits
     * </p>
     */
    protected static final WireMockServer WIRE_MOCK_SERVER;

    static {
        // Start Redis container
        REDIS_CONTAINER = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                .withExposedPorts(6379);
        REDIS_CONTAINER.start();

        // Start WireMock server
        WIRE_MOCK_SERVER = new WireMockServer(
                WireMockConfiguration.options()
                        .dynamicPort()
        );
        WIRE_MOCK_SERVER.start();
    }

    /**
     * Configure Spring properties dynamically from singleton Testcontainers and WireMock.
     * <p>
     * Called ONCE per test class, but always returns same ports from singleton instances.
     * This prevents "Connection reset" errors and WireMock port mismatches.
     * </p>
     */
    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        // Redis configuration
        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379));

        // Auth API base URL (WireMock)
        registry.add("menta.auth.base-url", () -> "http://localhost:" + WIRE_MOCK_SERVER.port());
    }
}
