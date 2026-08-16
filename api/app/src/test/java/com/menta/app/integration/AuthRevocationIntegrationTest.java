package com.menta.app.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.menta.app.outbox.OutboxBlacklistReconciler;
import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetAttemptRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetRequestRateLimitPort;
import com.menta.auth.application.port.out.RateLimitDecision;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.auth.infrastructure.persistence.repository.OutboxRowJpaRepository;
import com.menta.shared.domain.vo.Email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end coverage for #88's revocation mechanism: refresh-reuse detection
 * must actually invalidate the access tokens it claims to invalidate, family
 * revocation must not spill onto other users, and a revoked-but-well-formed
 * Bearer token must come back as a proper RFC 9457 401 — not a 500 or a
 * silently-accepted request.
 *
 * <p>Runs against real MySQL (Testcontainers), not the H2 {@code test}
 * profile that {@link AuthFlowIntegrationTest} uses: the outbox payload
 * column is {@code columnDefinition = "JSON"}, and under H2 the write/read
 * path for a plain {@code String} field mapped to that type is NOT
 * symmetric — a payload written as a JSON object round-trips as a JSON
 * TEXT NODE, silently breaking every consumer's {@code payload.get("field")}
 * lookup. Real MySQL round-trips it correctly (verified empirically). This
 * class needs the reconciler to actually read what the use cases wrote, so
 * it cannot use the H2 slice without exercising that trap.</p>
 *
 * <p>{@code TokenBlacklistPort} is mocked (no live Redis in this slice) but
 * backed by real in-memory maps rather than blind stubs, so the projected
 * tokenVersion set by one call is actually visible to the next — otherwise
 * the test would only prove the mock was invoked, not that revocation works.
 * {@code OutboxAppender} is deliberately left real: the point is to prove the
 * outbox → reconciler → Redis-projection → filter pipeline end-to-end, not
 * to bypass it.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@Testcontainers
class AuthRevocationIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("menta_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    private static final String ADMIN_PROBE = "/api/v1/admin/probe";
    private static final String RAW_PASSWORD = "SecurePass123!";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private OutboxBlacklistReconciler reconciler;

    @Autowired
    private OutboxRowJpaRepository outboxRowJpaRepository;

    @MockBean
    private AuthDegradedGuard authDegradedGuard;

    @MockBean
    private TokenBlacklistPort tokenBlacklistPort;

    @MockBean
    private LoginRateLimitPort loginRateLimitPort;

    /**
     * Redis autoconfiguration is excluded under the {@code integration-test}
     * profile (no live Redis alongside the MySQL container), so every other
     * Redis-backed port must be mocked too — otherwise Spring fails to
     * construct their real implementations at context startup for lack of a
     * {@code RedisTemplate} bean. Mirrors {@code AccountActivationIntegrationTest}.
     */
    @MockBean
    private ActivationRateLimitPort activationRateLimitPort;

    @MockBean
    private PasswordResetRequestRateLimitPort passwordResetRequestRateLimitPort;

    @MockBean
    private PasswordResetAttemptRateLimitPort passwordResetAttemptRateLimitPort;

    private final Map<String, Long> projectedVersions = new ConcurrentHashMap<>();
    private final Set<String> blacklistedJtis = ConcurrentHashMap.newKeySet();

    @BeforeEach
    void wireFakes() {
        // Each test drives the reconciler across the whole outbox table; a
        // row left over from an earlier test in this class (real MySQL
        // container, shared across @Test methods) must not be picked up by
        // a later one's processBatch() call.
        outboxRowJpaRepository.deleteAll();
        when(authDegradedGuard.isDegraded()).thenReturn(false);
        when(loginRateLimitPort.check(anyString(), anyString()))
            .thenReturn(RateLimitDecision.allowed());

        // Stateful fakes, not blind stubs: a projection written by one call
        // must be visible to a later read, otherwise this test would only
        // prove the mock was invoked, not that revocation actually works.
        projectedVersions.clear();
        blacklistedJtis.clear();
        lenient().when(tokenBlacklistPort.currentTokenVersion(anyString())).thenAnswer(inv -> {
            Long version = projectedVersions.get((String) inv.getArgument(0));
            return version == null ? OptionalLong.empty() : OptionalLong.of(version);
        });
        lenient().doAnswer(inv -> {
            String userId = inv.getArgument(0);
            long version = inv.getArgument(1);
            // Mirrors the production Lua script's monotonic guard (#88):
            // never let a lower/out-of-order projection win.
            projectedVersions.merge(userId, version, Math::max);
            return null;
        }).when(tokenBlacklistPort).projectTokenVersion(anyString(), anyLong());
        lenient().when(tokenBlacklistPort.isBlacklisted(anyString()))
            .thenAnswer(inv -> blacklistedJtis.contains((String) inv.getArgument(0)));
        lenient().doAnswer(inv -> {
            blacklistedJtis.add((String) inv.getArgument(0));
            return null;
        }).when(tokenBlacklistPort).blacklist(anyString(), any(Duration.class));
    }

    @Test
    void refresh_reuse_detection_invalidates_every_access_token_already_issued() {
        String email = "revoked-" + System.nanoTime() + "@example.com";
        userRepository.save(User.create(Email.of(email), passwordEncoder.encode(RAW_PASSWORD), Role.STUDENT));

        ResponseEntity<Map> loginResponse = http.exchange(
            "/api/v1/auth/login", HttpMethod.POST,
            jsonEntity(Map.of("email", email, "password", RAW_PASSWORD)), Map.class
        );
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String originalRefresh = loginResponse.getHeaders().getFirst("X-Refresh-Token");

        // Sanity: the freshly issued access token authenticates (403, not
        // 401 — a STUDENT is authenticated but not authorized for /admin).
        String originalAccess = (String) loginResponse.getBody().get("access_token");
        assertThat(adminProbeStatus(originalAccess)).isEqualTo(HttpStatus.FORBIDDEN);

        // Rotate once: originalRefresh becomes USED, a new pair is issued.
        ResponseEntity<Map> rotateResponse = http.exchange(
            "/api/v1/auth/refresh", HttpMethod.POST, refreshEntity(originalRefresh), Map.class
        );
        assertThat(rotateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String rotatedAccess = (String) rotateResponse.getBody().get("access_token");
        assertThat(adminProbeStatus(rotatedAccess)).isEqualTo(HttpStatus.FORBIDDEN);

        // Present the already-USED refresh a second time: reuse detection
        // must revoke the whole family and bump tokenVersion.
        ResponseEntity<Map> reuseResponse = http.exchange(
            "/api/v1/auth/refresh", HttpMethod.POST, refreshEntity(originalRefresh), Map.class
        );
        assertThat(reuseResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // The bump is only in MySQL + an outbox row until the reconciler
        // projects it — this is the "escritura" half of #88.
        reconciler.processBatch();

        // The "lectura" half: both the pre-rotation and the just-rotated
        // access token must now be refused, not merely the reused refresh.
        assertThat(adminProbeStatus(originalAccess))
            .as("token issued before the compromise must be invalidated")
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(adminProbeStatus(rotatedAccess))
            .as("token issued by the rotation immediately preceding the compromise must be invalidated too")
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void revoking_one_users_family_does_not_touch_another_users_session() {
        String victimEmail = "victim-" + System.nanoTime() + "@example.com";
        String bystanderEmail = "bystander-" + System.nanoTime() + "@example.com";
        userRepository.save(User.create(Email.of(victimEmail), passwordEncoder.encode(RAW_PASSWORD), Role.STUDENT));
        userRepository.save(User.create(Email.of(bystanderEmail), passwordEncoder.encode(RAW_PASSWORD), Role.STUDENT));

        ResponseEntity<Map> victimLogin = http.exchange(
            "/api/v1/auth/login", HttpMethod.POST,
            jsonEntity(Map.of("email", victimEmail, "password", RAW_PASSWORD)), Map.class
        );
        String victimRefresh = victimLogin.getHeaders().getFirst("X-Refresh-Token");

        ResponseEntity<Map> bystanderLogin = http.exchange(
            "/api/v1/auth/login", HttpMethod.POST,
            jsonEntity(Map.of("email", bystanderEmail, "password", RAW_PASSWORD)), Map.class
        );
        String bystanderAccess = (String) bystanderLogin.getBody().get("access_token");

        // Rotate then reuse the victim's original refresh to trigger family
        // revocation on the victim only.
        http.exchange("/api/v1/auth/refresh", HttpMethod.POST, refreshEntity(victimRefresh), Map.class);
        ResponseEntity<Map> reuse = http.exchange(
            "/api/v1/auth/refresh", HttpMethod.POST, refreshEntity(victimRefresh), Map.class
        );
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        reconciler.processBatch();

        assertThat(adminProbeStatus(bystanderAccess))
            .as("an uninvolved user's session must survive another user's family revocation")
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void a_revoked_access_token_comes_back_as_a_proper_rfc_9457_401() {
        String email = "shape-" + System.nanoTime() + "@example.com";
        userRepository.save(User.create(Email.of(email), passwordEncoder.encode(RAW_PASSWORD), Role.STUDENT));

        ResponseEntity<Map> loginResponse = http.exchange(
            "/api/v1/auth/login", HttpMethod.POST,
            jsonEntity(Map.of("email", email, "password", RAW_PASSWORD)), Map.class
        );
        String refresh = loginResponse.getHeaders().getFirst("X-Refresh-Token");
        String access = (String) loginResponse.getBody().get("access_token");

        http.exchange("/api/v1/auth/refresh", HttpMethod.POST, refreshEntity(refresh), Map.class);
        http.exchange("/api/v1/auth/refresh", HttpMethod.POST, refreshEntity(refresh), Map.class);
        reconciler.processBatch();

        ResponseEntity<Map> response = http.exchange(
            ADMIN_PROBE, HttpMethod.GET, bearerEntity(access), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).containsEntry("code", "AUTHENTICATION_REQUIRED");
    }

    private HttpStatusCode adminProbeStatus(String accessToken) {
        return http.exchange(ADMIN_PROBE, HttpMethod.GET, bearerEntity(accessToken), Map.class)
            .getStatusCode();
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

    private static HttpEntity<Void> bearerEntity(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }
}
