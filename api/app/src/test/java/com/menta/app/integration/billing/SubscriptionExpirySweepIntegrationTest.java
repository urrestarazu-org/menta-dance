package com.menta.app.integration.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.billing.infrastructure.persistence.entity.SubscriptionCourseJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.SubscriptionJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.SubscriptionCourseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.SubscriptionJpaRepository;
import com.menta.billing.infrastructure.scheduling.SubscriptionExpiryReconciler;
import com.menta.billing.infrastructure.scheduling.SubscriptionExpiryWorker;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Real {@code tick()} coverage for {@link SubscriptionExpiryReconciler}/{@link
 * SubscriptionExpiryWorker} (US-BILLING-012, design A6/A6b/A2/A13) against a real MySQL
 * (Testcontainers). {@code billing.subscription.expiry.rate-ms} is set very high so the
 * {@code @Scheduled} job never fires on its own during the test — every assertion drives {@code
 * tick()} manually.
 *
 * <p>{@code webEnvironment = NONE}: this proves the sweep's persistence behaviour and bean
 * wiring, not an HTTP contract. Mirrors {@code UserExistenceCrossModuleIntegrationTest}'s
 * {@code NONE} shape and its minimal mock set — mocking {@code RedisTemplate} directly is
 * enough to satisfy every Redis-backed rate limiter bean the full {@code com.menta.app}
 * context assembles; the checkout/payment/course-catalog ports resolve to their real
 * component-scanned adapters since this test never exercises those flows.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration-test")
@Testcontainers
class SubscriptionExpirySweepIntegrationTest {

    private static final String HMAC_SECRET = "integration-test-expiry-secret";
    private static final String MERCHANT_ACCOUNT_ID = "merchant-expiry";
    private static final String COURSE_ID = "course-1";

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
        // The scheduled tick must never fire on its own — every assertion drives tick() by hand.
        registry.add("billing.subscription.expiry.rate-ms", () -> "999999999");
    }

    @Autowired private SubscriptionJpaRepository subscriptionRepository;
    @Autowired private SubscriptionCourseJpaRepository subscriptionCourseRepository;
    @Autowired private SubscriptionExpiryReconciler reconciler;
    @Autowired private ApplicationContext context;

    @MockBean private AuthDegradedGuard authDegradedGuard;
    @MockBean private TokenBlacklistPort tokenBlacklistPort;
    @MockBean private RedisTemplate<String, String> redisTemplate;
    @MockBean private LoginRateLimitPort loginRateLimitPort;

    @AfterEach
    void cleanUp() {
        subscriptionCourseRepository.deleteAll();
        subscriptionRepository.deleteAll();
    }

    // --- fixtures -------------------------------------------------------------

    /**
     * {@code Instant.now()} truncated to microseconds — the precision the auto-generated
     * {@code datetime(6)} column for {@code end_date} actually preserves on a MySQL round-trip
     * (this test class runs with {@code ddl-auto: create-drop}, so the schema comes from
     * Hibernate's own {@code Instant} mapping, not the {@code V18} migration's explicit DDL).
     * The JVM's real clock resolution is platform-dependent: on macOS {@link Instant#now()}
     * already only ever produces microsecond-aligned nanoseconds, so a raw value round-trips
     * losslessly there, but on Linux (this project's CI) it commonly carries genuine
     * sub-microsecond entropy that MySQL silently rounds away — comparing the read-back {@code
     * end_date} against an untruncated fixture value then fails non-deterministically depending
     * on the host, not on any interference between test classes. Truncating at fixture creation
     * keeps the expected and persisted values byte-identical everywhere.
     */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private UUID seedSubscription(String status, String type, Instant startDate, Instant endDate, long version) {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID activeUserId = ("ACTIVE".equals(status) || "PENDING".equals(status)) ? userId : null;
        boolean trial = "TRIAL".equals(type);
        UUID paymentId = trial ? null : UUID.randomUUID();
        Instant grantedAt = trial ? startDate : null;
        UUID grantedBy = trial ? UUID.randomUUID() : null;
        String grantReason = trial ? "motivo de prueba" : null;
        Integer grantDays = trial ? 7 : null;
        subscriptionRepository.save(new SubscriptionJpaEntity(
            id, paymentId, userId, UUID.randomUUID(), "idem-" + id, activeUserId, status, "ASSIGNED", startDate,
            endDate, null, null, startDate, null, null, null, type, grantedAt, grantedBy, grantReason, grantDays,
            version
        ));
        return id;
    }

    /** A row that fails to map back to the domain: PAID with no payment violates A17. */
    private UUID seedMalformedActivePaidWithNoPayment(Instant startDate, Instant endDate) {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        subscriptionRepository.save(new SubscriptionJpaEntity(
            id, null, userId, UUID.randomUUID(), "idem-" + id, userId, "ACTIVE", "ASSIGNED", startDate, endDate,
            null, null, startDate, null, null, null, "PAID", null, null, null, null, 0L
        ));
        return id;
    }

    // --- Escenario "A stale paid subscription expires automatically" [S7] -----
    // --- Escenario "A stale trial expires automatically" [S6] -----------------

    @Test
    void stalePaidAndTrial() {
        Instant now = now();
        Instant past = now.minusSeconds(3600);
        UUID stalePaidId = seedSubscription("ACTIVE", "PAID", past.minusSeconds(600), past, 0L);
        UUID staleTrialId = seedSubscription("ACTIVE", "TRIAL", past.minusSeconds(600), past, 0L);

        reconciler.tick();

        assertThat(subscriptionRepository.findById(stalePaidId).orElseThrow().getStatus()).isEqualTo("EXPIRED");
        assertThat(subscriptionRepository.findById(staleTrialId).orElseThrow().getStatus()).isEqualTo("EXPIRED");
        assertThat(subscriptionRepository.findById(stalePaidId).orElseThrow().getEndDate()).isEqualTo(past);
        assertThat(subscriptionRepository.findById(staleTrialId).orElseThrow().getEndDate()).isEqualTo(past);
    }

    // --- Escenario "Non-active subscriptions are left untouched" [S8] ---------

    @Test
    void untouched() {
        Instant now = now();
        Instant past = now.minusSeconds(3600);
        UUID cancelledId = seedSubscription("CANCELLED", "PAID", past.minusSeconds(600), past, 0L);
        UUID alreadyExpiredId = seedSubscription("EXPIRED", "PAID", past.minusSeconds(600), past, 0L);

        reconciler.tick();

        SubscriptionJpaEntity cancelled = subscriptionRepository.findById(cancelledId).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
        assertThat(cancelled.getEndDate()).isEqualTo(past);
        SubscriptionJpaEntity alreadyExpired = subscriptionRepository.findById(alreadyExpiredId).orElseThrow();
        assertThat(alreadyExpired.getStatus()).isEqualTo("EXPIRED");
        assertThat(alreadyExpired.getEndDate()).isEqualTo(past);
    }

    // --- Batch resilience (design A6) — one bad row never aborts the batch ----

    @Test
    void batchResilience() {
        Instant now = now();
        Instant past = now.minusSeconds(3600);
        UUID malformedId = seedMalformedActivePaidWithNoPayment(past.minusSeconds(600), past);
        UUID healthyId = seedSubscription("ACTIVE", "PAID", past.minusSeconds(600), past, 0L);
        subscriptionCourseRepository.save(new SubscriptionCourseJpaEntity(healthyId, COURSE_ID));

        reconciler.tick();

        // The malformed row failed to map (A17 invariant) and is left untouched.
        assertThat(subscriptionRepository.findById(malformedId).orElseThrow().getStatus()).isEqualTo("ACTIVE");
        // The healthy row still expired despite the other row's failure.
        SubscriptionJpaEntity healthy = subscriptionRepository.findById(healthyId).orElseThrow();
        assertThat(healthy.getStatus()).isEqualTo("EXPIRED");
        // save() → replaceCourses() rewrote the snapshot from the hydrated aggregate, not an
        // empty one — the course row is still there.
        assertThat(subscriptionCourseRepository.findBySubscriptionId(healthyId))
            .extracting("courseId")
            .containsExactly(COURSE_ID);
    }

    // --- Wiring (scan, A6b) ----------------------------------------------------

    @Test
    void sweepBeansAreScannedOnce() {
        assertThat(context.getBeansOfType(SubscriptionExpiryReconciler.class)).hasSize(1);
        assertThat(context.getBeansOfType(SubscriptionExpiryWorker.class)).hasSize(1);
    }

    @BeforeEach
    void stubDefensively() {
        when(tokenBlacklistPort.isBlacklisted(anyString())).thenReturn(false);
        when(tokenBlacklistPort.currentTokenVersion(anyString())).thenReturn(java.util.OptionalLong.empty());
        when(authDegradedGuard.isDegraded()).thenReturn(false);
    }
}
