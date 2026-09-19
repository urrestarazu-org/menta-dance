package com.menta.app.integration.physical;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetAttemptRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetRequestRateLimitPort;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.billing.application.port.out.BankTransferRateLimitPort;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.application.port.out.PaymentProviderPort;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityHoldJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalSessionJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityHoldJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCourseJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository;
import com.menta.physical.infrastructure.scheduling.HoldExpiryReconciler;
import com.menta.physical.infrastructure.scheduling.HoldExpiryWorker;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
 * Real {@code sweep()} coverage for {@link HoldExpiryWorker} against a real MySQL (Testcontainers)
 * (#208, US-PHYSICAL-004b, design B5, tasks Phase 9 — task 9.4). Housekeeping only: an expired
 * hold already stops counting against availability with zero sweep runs (proven by PR3/PR4's own
 * suites — {@code countActiveBySessionIdForUpdate} and the availability queries both filter
 * {@code converted_at IS NULL AND expires_at > :now} at read time). This test instead proves the
 * SWEEP ITSELF: three seeded rows — expired-unconverted, old-converted, still-active — and only
 * the first two are gone after {@link HoldExpiryWorker#sweep()}.
 *
 * <p>{@code physical.capacity.hold.expiry.rate-ms} is set very high so the {@code @Scheduled} job
 * never fires on its own during the test — the assertion drives {@code sweep()} manually. Mirrors
 * {@code SubscriptionExpirySweepIntegrationTest}'s shape.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration-test")
@Testcontainers
class HoldExpiryWorkerIntegrationTest {

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
        // The scheduled tick must never fire on its own — every assertion drives sweep() by hand.
        registry.add("physical.capacity.hold.expiry.rate-ms", () -> "999999999");
        // The shared integration-test profile defaults this reconciler off (see
        // application-integration-test.yml); this class is the one place that needs the real
        // bean to exist, since it wires HoldExpiryReconciler directly.
        registry.add("physical.capacity.hold.expiry.enabled", () -> "true");
        registry.add("physical.capacity.hold.ttl-ms", () -> "1800000");
    }

    @Autowired private PhysicalCourseJpaRepository courseRepository;
    @Autowired private PhysicalSessionJpaRepository sessionRepository;
    @Autowired private PhysicalCapacityHoldJpaRepository holdRepository;
    @Autowired private HoldExpiryWorker worker;
    @Autowired private ApplicationContext context;

    @MockBean private AuthDegradedGuard authDegradedGuard;
    @MockBean private TokenBlacklistPort tokenBlacklistPort;
    @MockBean private LoginRateLimitPort loginRateLimitPort;
    @MockBean private ActivationRateLimitPort activationRateLimitPort;
    @MockBean private PasswordResetRequestRateLimitPort passwordResetRequestRateLimitPort;
    @MockBean private PasswordResetAttemptRateLimitPort passwordResetAttemptRateLimitPort;
    @MockBean private BillingPlansRateLimitPort billingPlansRateLimitPort;
    @MockBean private BankTransferRateLimitPort bankTransferRateLimitPort;
    @MockBean private CourseCatalogPort courseCatalogPort;
    @MockBean private PaymentProviderPort paymentProviderPort;

    @SuppressWarnings("rawtypes")
    @MockBean
    private RedisTemplate redisTemplate;

    @AfterEach
    void cleanUp() {
        holdRepository.deleteAll();
        sessionRepository.deleteAll();
        courseRepository.deleteAll();
    }

    private UUID seedSession(int capacity) {
        UUID professorId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Instant now = now();
        courseRepository.save(new PhysicalCourseJpaEntity(
            courseId, "Test course", "desc", professorId, "Maria Garcia",
            "WEDNESDAY", LocalTime.of(20, 0), 60, "INTERMEDIATE", capacity,
            CourseStatus.ACTIVE, now, now
        ));
        UUID sessionId = UUID.randomUUID();
        sessionRepository.save(new PhysicalSessionJpaEntity(
            sessionId, courseId, now.plusSeconds(86400), capacity,
            "SCHEDULED", null
        ));
        return sessionId;
    }

    private UUID seedHold(UUID sessionId, Instant expiresAt, Instant convertedAt) {
        UUID id = UUID.randomUUID();
        holdRepository.save(new PhysicalCapacityHoldJpaEntity(
            id, sessionId, UUID.randomUUID(), expiresAt, convertedAt, now().minusSeconds(3600)
        ));
        return id;
    }

    /** {@code Instant.now()} truncated to microseconds — see {@code SubscriptionExpirySweepIntegrationTest}. */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    @Test
    void sweep_deletes_only_the_expired_unconverted_and_the_old_converted_rows() {
        UUID sessionId = seedSession(3);
        Instant now = now();

        UUID expiredUnconverted = seedHold(sessionId, now.minusSeconds(60), null);
        UUID oldConverted = seedHold(sessionId, now.plusSeconds(1800), now.minus(2, ChronoUnit.DAYS));
        UUID stillActive = seedHold(sessionId, now.plusSeconds(1800), null);

        worker.sweep();

        assertThat(holdRepository.findById(expiredUnconverted)).isEmpty();
        assertThat(holdRepository.findById(oldConverted)).isEmpty();
        assertThat(holdRepository.findById(stillActive)).isPresent();
    }

    @Test
    void sweep_leaves_a_recently_converted_row_alone() {
        UUID sessionId = seedSession(3);
        Instant now = now();

        UUID recentlyConverted = seedHold(sessionId, now.plusSeconds(1800), now.minusSeconds(60));

        worker.sweep();

        assertThat(holdRepository.findById(recentlyConverted)).isPresent();
    }

    @Test
    void holdExpiryBeansAreScannedOnce() {
        assertThat(context.getBeansOfType(HoldExpiryReconciler.class)).hasSize(1);
        assertThat(context.getBeansOfType(HoldExpiryWorker.class)).hasSize(1);
    }
}
