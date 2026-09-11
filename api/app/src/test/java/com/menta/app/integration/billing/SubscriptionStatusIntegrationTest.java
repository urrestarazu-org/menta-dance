package com.menta.app.integration.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.menta.auth.application.port.out.AccessTokenIssuer;
import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetAttemptRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetRequestRateLimitPort;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.application.port.out.PaymentPreferencePort;
import com.menta.billing.application.port.out.PaymentProviderPort;
import com.menta.billing.infrastructure.persistence.entity.SubscriptionJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.SubscriptionCourseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.SubscriptionJpaRepository;
import com.menta.billing.infrastructure.scheduling.SubscriptionExpiryReconciler;
import com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase;
import com.menta.shared.domain.vo.Email;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end coverage for US-BILLING-004 (#32): the two read-side subscription endpoints
 * ({@code GET /me}, {@code GET /me/history}), over the real {@code SecurityConfig} filter chain
 * and MySQL persistence. Mirrors {@code SubscriptionCancellationIntegrationTest}'s harness.
 *
 * <p>Rows are seeded directly through {@link SubscriptionJpaRepository} rather than driven
 * through checkout + webhook — this test owns {@code status}/{@code startDate}/{@code endDate}
 * precisely, which is what every one of the 6 issue scenarios discriminates on, and mirrors
 * {@code SubscriptionExpirySweepIntegrationTest}'s own direct-seeding fixture style.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@Testcontainers
class SubscriptionStatusIntegrationTest {

    private static final String HMAC_SECRET = "integration-test-status-secret";
    private static final String MERCHANT_ACCOUNT_ID = "merchant-status";
    private static final String PLANS_URL = "/api/v1/billing/plans";

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
        registry.add("billing.webhook.mercadopago.hmac-secret", () -> HMAC_SECRET);
        registry.add("billing.webhook.reconcile-rate-ms", () -> "999999999");
        registry.add("billing.mercadopago.merchant-account-id", () -> MERCHANT_ACCOUNT_ID);
        // The scheduled tick must never fire on its own — the post-sweep scenario drives tick()
        // by hand, mirroring SubscriptionExpirySweepIntegrationTest.
        registry.add("billing.subscription.expiry.rate-ms", () -> "999999999");
        registry.add("billing.subscription.expiry.enabled", () -> "true");
    }

    @Autowired private TestRestTemplate http;
    @Autowired private UserRepository userRepository;
    @Autowired private AccessTokenIssuer accessTokenIssuer;
    @Autowired private SubscriptionJpaRepository subscriptionRepository;
    @Autowired private SubscriptionCourseJpaRepository subscriptionCourseRepository;
    @Autowired private SubscriptionExpiryReconciler reconciler;

    @MockBean private PaymentPreferencePort paymentPreferencePort;
    @MockBean private PaymentProviderPort paymentProviderPort;
    @MockBean private BillingPlansRateLimitPort billingPlansRateLimitPort;
    @MockBean private CourseCatalogPort courseCatalogPort;
    @MockBean private AuthDegradedGuard authDegradedGuard;
    @MockBean private TokenBlacklistPort tokenBlacklistPort;
    @MockBean private LoginRateLimitPort loginRateLimitPort;
    @MockBean private ActivationRateLimitPort activationRateLimitPort;
    @MockBean private PasswordResetRequestRateLimitPort passwordResetRequestRateLimitPort;
    @MockBean private PasswordResetAttemptRateLimitPort passwordResetAttemptRateLimitPort;
    @MockBean private ProcessPhysicalCheckInUseCase processPhysicalCheckInUseCase;

    @BeforeEach
    void stubDefensively() {
        when(tokenBlacklistPort.isBlacklisted(anyString())).thenReturn(false);
        when(tokenBlacklistPort.currentTokenVersion(anyString())).thenReturn(java.util.OptionalLong.empty());
        when(authDegradedGuard.isDegraded()).thenReturn(false);
    }

    @AfterEach
    void cleanUp() {
        subscriptionCourseRepository.deleteAll();
        subscriptionRepository.deleteAll();
    }

    // --- fixtures -------------------------------------------------------------

    /** Truncated to microseconds — the precision MySQL's {@code datetime(6)} column preserves. */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private UUID seedUser() {
        User user = User.create(Email.of("user-" + UUID.randomUUID() + "@example.com"), "irrelevant-hash",
            Role.STUDENT);
        userRepository.save(user);
        return user.getId().getValue();
    }

    private HttpHeaders headersFor(UUID userId) {
        User user = new User(
            UserId.of(userId), Email.of("token-" + UUID.randomUUID() + "@example.com"), "hash", Role.STUDENT,
            UserStatus.ACTIVE, java.time.LocalDateTime.now(), java.time.LocalDateTime.now()
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessTokenIssuer.issue(user).token());
        return headers;
    }

    private UUID seedSubscription(
        UUID userId, String status, Instant startDate, Instant endDate, String checkoutUrl, Instant createdAt
    ) {
        UUID id = UUID.randomUUID();
        UUID activeUserId = ("ACTIVE".equals(status) || "PENDING".equals(status)) ? userId : null;
        // A PAID subscription always requires a non-null paymentId (domain invariant A17) — a
        // PENDING row is created together with its Payment, before any provider call.
        UUID paymentId = UUID.randomUUID();
        subscriptionRepository.save(new SubscriptionJpaEntity(
            id, paymentId, userId, UUID.randomUUID(), "idem-" + id, activeUserId, status, "ASSIGNED",
            startDate, endDate, null, checkoutUrl, createdAt, null, null, null, "PAID", null, null, null, null, 0L
        ));
        return id;
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> currentOwn(UUID userId) {
        return http.exchange(
            "/api/v1/billing/subscriptions/me", HttpMethod.GET, new HttpEntity<>(headersFor(userId)), Map.class
        );
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<List> historyOwn(UUID userId) {
        return http.exchange(
            "/api/v1/billing/subscriptions/me/history", HttpMethod.GET, new HttpEntity<>(headersFor(userId)),
            List.class
        );
    }

    // --- Escenario 1: active subscription --------------------------------------

    @Test
    void an_active_subscription_returns_plan_dates_and_days_remaining() {
        UUID userId = seedUser();
        Instant now = now();
        Instant endDate = now.plus(20, ChronoUnit.DAYS);
        seedSubscription(userId, "ACTIVE", now.minus(10, ChronoUnit.DAYS), endDate, null, now);

        ResponseEntity<Map> response = currentOwn(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("ACTIVE");
        assertThat(response.getBody().get("subscriptionId")).isNotNull();
        assertThat(response.getBody().get("planId")).isNotNull();
        assertThat(response.getBody().get("startDate")).isNotNull();
        assertThat(response.getBody().get("endDate")).isNotNull();
        assertThat(response.getBody().get("daysRemaining")).isEqualTo(20);
    }

    // --- Escenario 2: expiring soon --------------------------------------------

    @Test
    void an_active_subscription_within_seven_days_of_expiry_sets_expiring_soon() {
        UUID userId = seedUser();
        Instant now = now();
        Instant endDate = now.plus(7, ChronoUnit.DAYS);
        seedSubscription(userId, "ACTIVE", now.minus(23, ChronoUnit.DAYS), endDate, null, now);

        ResponseEntity<Map> response = currentOwn(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("expiringSoon")).isEqualTo(true);
    }

    // --- Escenario 3: expired subscription --------------------------------------

    @Test
    void an_expired_subscription_returns_200_with_a_plans_hint() {
        UUID userId = seedUser();
        Instant now = now();
        Instant endDate = now.minus(1, ChronoUnit.DAYS);
        seedSubscription(userId, "EXPIRED", now.minus(31, ChronoUnit.DAYS), endDate, null, now);

        ResponseEntity<Map> response = currentOwn(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("EXPIRED");
        assertThat(response.getBody().get("plansUrl")).isEqualTo(PLANS_URL);
        assertThat(response.getBody().get("daysRemaining")).isNull();
    }

    // --- Escenario 4: no subscription at all -------------------------------------

    @Test
    void a_user_with_no_subscription_rows_gets_404_with_a_distinct_error_code() {
        UUID userId = seedUser();

        ResponseEntity<Map> response = currentOwn(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("NO_SUBSCRIPTION");
        assertThat(response.getBody().get("code")).isNotEqualTo("SUBSCRIPTION_NOT_FOUND");
    }

    // --- Escenario 5: history --------------------------------------------------

    @Test
    void history_returns_every_row_newest_first() {
        UUID userId = seedUser();
        Instant now = now();
        UUID oldest = seedSubscription(
            userId, "EXPIRED", now.minus(90, ChronoUnit.DAYS), now.minus(60, ChronoUnit.DAYS), null,
            now.minus(90, ChronoUnit.DAYS)
        );
        UUID middle = seedSubscription(
            userId, "EXPIRED", now.minus(59, ChronoUnit.DAYS), now.minus(30, ChronoUnit.DAYS), null,
            now.minus(59, ChronoUnit.DAYS)
        );
        UUID newest = seedSubscription(
            userId, "EXPIRED", now.minus(29, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS), null,
            now.minus(29, ChronoUnit.DAYS)
        );

        ResponseEntity<List> response = historyOwn(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> body = response.getBody();
        assertThat(body).hasSize(3);
        assertThat(body.stream().map(row -> row.get("id")).toList())
            .containsExactly(newest.toString(), middle.toString(), oldest.toString());
    }

    // --- Escenario 6: pending payment --------------------------------------------

    @Test
    void a_pending_subscription_exposes_the_checkout_url_and_no_dates() {
        UUID userId = seedUser();
        Instant now = now();
        seedSubscription(userId, "PENDING", null, null, "https://mp.example/checkout/pref-1", now);

        ResponseEntity<Map> response = currentOwn(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("PENDING");
        assertThat(response.getBody().get("checkoutUrl")).isEqualTo("https://mp.example/checkout/pref-1");
        assertThat(response.getBody().get("startDate")).isNull();
        assertThat(response.getBody().get("endDate")).isNull();
        assertThat(response.getBody().get("daysRemaining")).isNull();
    }

    // --- Escenario 7: no authentication ------------------------------------------

    @Test
    void an_unauthenticated_request_to_current_is_rejected() {
        ResponseEntity<Map> response = http.exchange(
            "/api/v1/billing/subscriptions/me", HttpMethod.GET, HttpEntity.EMPTY, Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void an_unauthenticated_request_to_history_is_rejected() {
        ResponseEntity<String> response = http.exchange(
            "/api/v1/billing/subscriptions/me/history", HttpMethod.GET, HttpEntity.EMPTY, String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- Escenario 8: immediate reflection after the expiry sweep ----------------

    @Test
    void a_status_flip_from_the_expiry_sweep_is_visible_immediately() {
        UUID userId = seedUser();
        Instant now = now();
        Instant pastEndDate = now.minus(1, ChronoUnit.HOURS);
        UUID subscriptionId = seedSubscription(
            userId, "ACTIVE", now.minus(30, ChronoUnit.DAYS), pastEndDate, null, now
        );

        reconciler.tick();

        assertThat(subscriptionRepository.findById(subscriptionId).orElseThrow().getStatus())
            .isEqualTo("EXPIRED");

        ResponseEntity<Map> response = currentOwn(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("EXPIRED");
    }
}
