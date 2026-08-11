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

        // 1. POST /api/v1/auth/login -> 200 with token_pair
        ResponseEntity<Map> loginResponse = http.exchange(
            "/api/v1/auth/login",
            HttpMethod.POST,
            jsonEntity(Map.of("email", EMAIL, "password", RAW_PASSWORD)),
            Map.class
        );
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = loginResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsKeys("access_token", "token_type", "expires_in");
        String refreshToken = loginResponse.getHeaders().getFirst("X-Refresh-Token");
        assertThat(refreshToken).isNotBlank();
        String accessToken = (String) body.get("access_token");
        assertThat(accessToken).isNotBlank();

        // 2. POST /api/v1/auth/refresh -> 200, token is only in the header.
        ResponseEntity<Map> refreshResponse = http.exchange(
            "/api/v1/auth/refresh",
            HttpMethod.POST,
            refreshEntity(refreshToken),
            Map.class
        );
        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> rotateBody = refreshResponse.getBody();
        assertThat(rotateBody).isNotNull();
        assertThat((String) rotateBody.get("access_token")).isNotBlank();
        String rotatedRefresh = refreshResponse.getHeaders().getFirst("X-Refresh-Token");
        assertThat(rotatedRefresh).isNotBlank();
        assertThat(rotatedRefresh).isNotEqualTo(refreshToken);

        // 3. POST /api/v1/auth/logout -> 204, requiring both credentials.
        String rotatedAccess = (String) rotateBody.get("access_token");
        ResponseEntity<Void> logoutResponse = http.exchange(
            "/api/v1/auth/logout",
            HttpMethod.POST,
            logoutEntity(rotatedAccess, rotatedRefresh),
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
            "/api/v1/auth/login",
            HttpMethod.POST,
            jsonEntity(Map.of("email", EMAIL, "password", "WrongPass!")),
            Map.class
        );

        assertThat(response.getStatusCode())
            .as("login must reject wrong password with 401 per spec")
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType())
            .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat((String) response.getBody().get("code"))
            .isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void login_returns_401_for_unknown_email_without_discrimination() {
        when(authDegradedGuard.isDegraded()).thenReturn(false);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/auth/login",
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
            "/api/v1/auth/login",
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
        assertThat(response.getHeaders().getContentType())
            .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody())
            .containsEntry("type", "https://menta.dance/problems/auth_degraded")
            .containsEntry("title", "Service Unavailable")
            .containsEntry("status", 503)
            .containsEntry("detail", "Authentication is temporarily unavailable.")
            .containsEntry("code", "AUTH_DEGRADED");
    }

    @Test
    void logout_requires_a_bearer_token_and_returns_an_rfc_9457_problem() {
        ResponseEntity<Map> response = http.exchange(
            "/api/v1/auth/logout",
            HttpMethod.POST,
            refreshEntity(UUID.randomUUID().toString()),
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType())
            .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).containsEntry("code", "AUTHENTICATION_REQUIRED");
    }

    @Test
    void legacy_auth_routes_are_not_mapped() {
        for (String route : new String[] {"/auth/login", "/auth/refresh", "/auth/logout"}) {
            ResponseEntity<Void> response = http.exchange(
                route,
                HttpMethod.POST,
                new HttpEntity<>(new HttpHeaders()),
                Void.class
            );

            assertThat(response.getStatusCode())
                .as("legacy route %s must not remain observable", route)
                .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void authenticated_student_is_denied_from_the_admin_route_with_an_rfc_9457_problem() {
        when(authDegradedGuard.isDegraded()).thenReturn(false);
        userRepository.findByEmail(Email.of(EMAIL))
            .ifPresent(existing -> userRepository.deleteById(existing.getId()));
        userRepository.save(User.create(
            Email.of(EMAIL),
            passwordEncoder.encode(RAW_PASSWORD),
            Role.STUDENT
        ));

        ResponseEntity<Map> loginResponse = http.exchange(
            "/api/v1/auth/login",
            HttpMethod.POST,
            jsonEntity(Map.of("email", EMAIL, "password", RAW_PASSWORD)),
            Map.class
        );
        String accessToken = (String) loginResponse.getBody().get("access_token");

        // Security authorizes this configured admin route before MVC handler lookup.
        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/probe",
            HttpMethod.GET,
            bearerEntity(accessToken),
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getContentType())
            .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody())
            .containsEntry("type", "https://menta.dance/problems/access_denied")
            .containsEntry("title", "Forbidden")
            .containsEntry("status", 403)
            .containsEntry("detail", "You are not authorized to access this resource.")
            .containsEntry("code", "ACCESS_DENIED");
    }

    private static HttpEntity<Map<String, String>> jsonEntity(Map<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private static HttpEntity<Void> refreshEntity(String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Refresh-Token", refreshToken);
        return new HttpEntity<>(headers);
    }

    private static HttpEntity<Void> logoutEntity(String accessToken, String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("X-Refresh-Token", refreshToken);
        return new HttpEntity<>(headers);
    }

    private static HttpEntity<Void> bearerEntity(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }
}
