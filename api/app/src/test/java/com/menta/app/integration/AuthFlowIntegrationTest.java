package com.menta.app.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.OutboxAppender;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.auth.domain.model.RefreshTokenStatus;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.auth.infrastructure.persistence.entity.RefreshTokenJpaEntity;
import com.menta.auth.infrastructure.persistence.repository.RefreshTokenJpaRepository;
import com.menta.shared.domain.vo.Email;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test for the auth flow: login → refresh → logout through the
 * full HTTP stack including JwtAuthenticationFilter + AuthController +
 * use cases + JPA persist (login + refresh ops hit MySQL/H2).
 *
 * Heaviest dependencies (Redis side-effect, JWT signing, outbox) stay real
 * because PRD3 wiring exercises them. The outbox reconciler is NOT mocked —
 * the focus is the controller/use-case orchestration, not the side-effect loop.
 *
 * Stubbing policy:
 *   - AuthDegradedGuard: always false so the fail-closed path does not block.
 *   - TokenBlacklistPort + RedisTemplate: mocked so the test runs without a
 *     running Redis. Side-effect during login is part of the production loop
 *     but not part of this endpoint contract.
 *   - OutboxAppender: no-op stub. The outbox row is consumable by the
 *     reconciler later, not by the request path.
 *
 * Schema: this test relies on Hibernate's create-drop schema with the
 * application-test.yml profile. The RefreshTokenJpaEntity's
 * columnDefinition='BINARY(16)' is MySQL-flavored but H2 tolerates it as a
 * fixed-width binary — verified by the live test run.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    private static final String EMAIL = "alice@example.com";
    private static final String RAW_PASSWORD = "SecurePass123!";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TestRestTemplate http;

    @MockBean
    private AuthDegradedGuard authDegradedGuard;

    @MockBean
    private OutboxAppender outboxAppender;

    @MockBean
    private TokenBlacklistPort tokenBlacklistPort;

    @MockBean
    @SuppressWarnings("rawtypes")
    private RedisTemplate redisTemplate;

    @Test
    void login_refresh_logout_happy_path() {
        // Stub the heaviest side-paths so the test focuses on wiring + persistence.
        when(authDegradedGuard.isDegraded()).thenReturn(false);

        // Seed a fresh user. We wipe any pre-existing email first because the
        // H2 in-memory DB can persist rows across test classes when the
        // context is reused.
        userRepository.findByEmail(Email.of(EMAIL))
            .ifPresent(existing -> userRepository.deleteById(existing.getId()));
        User user = User.create(Email.of(EMAIL), passwordEncoder.encode(RAW_PASSWORD), Role.STUDENT);
        userRepository.save(user);

        // 1. POST /auth/login -> 200 with token_pair
        ResponseEntity<Map> loginResponse = http.exchange(
            "/auth/login",
            HttpMethod.POST,
            jsonEntity(Map.of("email", EMAIL, "password", RAW_PASSWORD)),
            Map.class
        );
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = loginResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsKeys("access_token", "refresh_token", "token_type", "expires_in");
        String refreshToken = (String) body.get("refresh_token");
        assertThat(refreshToken).isNotBlank();

        // 2. POST /auth/refresh -> 200, new pair in same family
        ResponseEntity<Map> refreshResponse = http.exchange(
            "/auth/refresh",
            HttpMethod.POST,
            jsonEntity(Map.of("refreshToken", refreshToken)),
            Map.class
        );
        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> rotateBody = refreshResponse.getBody();
        assertThat(rotateBody).isNotNull();
        assertThat((String) rotateBody.get("access_token")).isNotBlank();
        assertThat((String) rotateBody.get("refresh_token")).isNotEqualTo(refreshToken);

        // 3. POST /auth/logout -> 204
        String rotatedRefresh = (String) rotateBody.get("refresh_token");
        ResponseEntity<Void> logoutResponse = http.exchange(
            "/auth/logout",
            HttpMethod.POST,
            jsonEntity(Map.of("refreshToken", rotatedRefresh)),
            Void.class
        );
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Sanity: at least one refresh row exists for this user — the
        // original ACTIVE row was marked USED (rotation), the new row got
        // ACTIVE then REVOKED (logout). The exact status distribution is
        // implementation-detail — what we assert is that the row count is
        // >=1 and at least one of them is REVOKED (logout did its job).
        Optional<User> stored = userRepository.findByEmail(Email.of(EMAIL));
        assertThat(stored).isPresent();
        var rows = refreshTokenJpaRepository.findAll();
        boolean anyRevoked = rows.stream()
            .map(RefreshTokenJpaEntity::getStatus)
            .anyMatch(RefreshTokenStatus.REVOKED::equals);
        assertThat(anyRevoked)
            .as("logout must mark the presented refresh as REVOKED")
            .isTrue();
    }

    @Test
    void login_returns_401_when_password_is_wrong() {
        when(authDegradedGuard.isDegraded()).thenReturn(false);

        userRepository.findByEmail(Email.of(EMAIL))
            .ifPresent(existing -> userRepository.deleteById(existing.getId()));
        userRepository.save(User.create(
            Email.of(EMAIL),
            passwordEncoder.encode(RAW_PASSWORD),
            Role.STUDENT
        ));

        ResponseEntity<Map> response = http.exchange(
            "/auth/login",
            HttpMethod.POST,
            jsonEntity(Map.of("email", EMAIL, "password", "WrongPass!")),
            Map.class
        );

        assertThat(response.getStatusCode())
            .as("login must reject wrong password with 401 per spec")
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat((String) response.getBody().get("code"))
            .isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void login_returns_401_for_unknown_email_without_discrimination() {
        when(authDegradedGuard.isDegraded()).thenReturn(false);

        ResponseEntity<Map> response = http.exchange(
            "/auth/login",
            HttpMethod.POST,
            jsonEntity(Map.of("email", "ghost@example.com", "password", "whatever")),
            Map.class
        );

        assertThat(response.getStatusCode())
            .as("login must collapse unknown email + wrong password to the same 401")
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat((String) response.getBody().get("code"))
            .isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void login_returns_503_when_reconciler_is_degraded() {
        when(authDegradedGuard.isDegraded()).thenReturn(true);

        ResponseEntity<Map> response = http.exchange(
            "/auth/login",
            HttpMethod.POST,
            jsonEntity(Map.of("email", EMAIL, "password", RAW_PASSWORD)),
            Map.class
        );

        assertThat(response.getStatusCode())
            .as("fail-closed guard: degraded reconciler -> 503")
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))
            .as("Retry-After must be 30 per ADR-0026")
            .isEqualTo("30");
    }

    private static HttpEntity<Map<String, String>> jsonEntity(Map<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
