package com.menta.app;

import com.menta.auth.application.port.out.TokenBlacklistPort;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration smoke test: verifies the Spring Boot application context loads.
 *
 * PR2 added JWT (JwtService) and Redis-backed (TokenBlacklistPortImpl,
 * OutboxBlacklistReconciler) components. The full Spring wiring — schema
 * validation, MySQL config, embedded Redis, JWT secret — belongs to
 * Phase 4 (PR3). This test stubs the new dependencies so the smoke
 * assertion keeps focusing on Spring Boot autowiring shape.
 */
@SpringBootTest
@ActiveProfiles("test")
class MentaDanceApplicationTest {

    @MockBean private RedisTemplate<String, String> redisTemplate;
    @MockBean private TokenBlacklistPort tokenBlacklistPort;
    @MockBean private com.menta.auth.infrastructure.outbox.persistence.UlidGenerator ulidGenerator;
    @MockBean private com.menta.auth.infrastructure.outbox.persistence.OutboxClock outboxClock;
    @MockBean private com.menta.auth.application.port.out.AccessTokenIssuer accessTokenIssuer;
    @MockBean private com.menta.auth.application.port.out.AuthDegradedGuard authDegradedGuard;

    @Test
    void contextLoads() {
        // Spring's contextLoader is the assertion: if any of the
        // registered beans cannot be created, this test fails.
    }
}
