package com.menta.app;

import com.menta.auth.application.port.out.TokenBlacklistPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration smoke test: verifies the Spring Boot application context loads.
 *
 * <p>PR2 added JWT (JwtService) and Redis-backed (TokenBlacklistPortImpl,
 * OutboxBlacklistReconciler) components. The full Spring wiring — schema
 * validation, MySQL config, embedded Redis, JWT secret — belongs to
 * Phase 4 (PR3). This test stubs the new dependencies so the smoke
 * assertion keeps focusing on Spring Boot autowiring shape.
 */
@SpringBootTest
@ActiveProfiles("test")
class MentaDanceApplicationTest {

    @MockitoBean private RedisTemplate<String, String> redisTemplate;
    @MockitoBean private TokenBlacklistPort tokenBlacklistPort;
    @MockitoBean private com.menta.auth.infrastructure.outbox.persistence.UlidGenerator ulidGenerator;
    @MockitoBean private com.menta.auth.infrastructure.outbox.persistence.OutboxClock outboxClock;
    @MockitoBean private com.menta.auth.application.port.out.AccessTokenIssuer accessTokenIssuer;
    @MockitoBean private com.menta.auth.application.port.out.AuthDegradedGuard authDegradedGuard;

    @Test
    void contextLoads() {
        // Spring's contextLoader is the assertion: if any of the
        // registered beans cannot be created, this test fails.
    }
}
