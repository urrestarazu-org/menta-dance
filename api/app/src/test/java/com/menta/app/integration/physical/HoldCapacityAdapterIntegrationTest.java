package com.menta.app.integration.physical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetAttemptRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetRequestRateLimitPort;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.application.port.out.PaymentProviderPort;
import com.menta.physical.application.port.in.PhysicalCapacityAssignmentPort;
import com.menta.physical.application.port.in.PhysicalCapacityHoldPort;
import com.menta.physical.domain.exception.CapacityBelowAssignedException;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityAssignmentJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalSessionJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityHoldJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCourseJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository;
import com.menta.shared.physical.CapacityAssignmentCommand;
import com.menta.shared.physical.MultiSessionCapacityHoldCommand;
import com.menta.shared.physical.SessionClaim;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Real-MySQL concurrency coverage of the hold write path (#208,
 * US-PHYSICAL-004b, design B4 — the proposal's High risk).
 *
 * <p>Clones {@link AssignCapacityAdapterIntegrationTest}'s shape and #217's
 * real {@link CountDownLatch} starting gun: a single-shot assertion on the
 * outcome is invisible to a probabilistic race, exactly as issue #216
 * measured (7 failures in 11 runs with an early plain read, 0 in 5
 * without). {@link org.junit.jupiter.api.RepeatedTest} runs each race
 * &ge;10 times so a race this test only catches "sometimes" still fails the
 * suite.</p>
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@Testcontainers
class HoldCapacityAdapterIntegrationTest {

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

    @Autowired private PhysicalCapacityHoldPort holdPort;
    @Autowired private PhysicalCapacityAssignmentPort assignmentPort;
    @Autowired private PhysicalCourseJpaRepository courseRepository;
    @Autowired private PhysicalSessionJpaRepository sessionRepository;
    @Autowired private PhysicalCapacityHoldJpaRepository holdRepository;
    @Autowired private PhysicalCapacityAssignmentJpaRepository assignmentRepository;

    @MockBean private AuthDegradedGuard authDegradedGuard;
    @MockBean private TokenBlacklistPort tokenBlacklistPort;
    @MockBean private LoginRateLimitPort loginRateLimitPort;
    @MockBean private ActivationRateLimitPort activationRateLimitPort;
    @MockBean private PasswordResetRequestRateLimitPort passwordResetRequestRateLimitPort;
    @MockBean private PasswordResetAttemptRateLimitPort passwordResetAttemptRateLimitPort;
    @MockBean private BillingPlansRateLimitPort billingPlansRateLimitPort;
    @MockBean private CourseCatalogPort courseCatalogPort;
    @MockBean private PaymentProviderPort paymentProviderPort;

    @SuppressWarnings("rawtypes")
    @MockBean
    private RedisTemplate redisTemplate;

    @AfterEach
    void cleanUp() {
        holdRepository.deleteAll();
        assignmentRepository.deleteAll();
        sessionRepository.deleteAll();
        courseRepository.deleteAll();
    }

    private UUID seedSession(int capacity) {
        UUID professorId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Instant now = Instant.now();
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

    private MultiSessionCapacityHoldCommand holdCommand(UUID sessionId, UUID paymentId) {
        return new MultiSessionCapacityHoldCommand(List.of(new SessionClaim(sessionId, Instant.now())), paymentId);
    }

    /**
     * TASK-3.5: capacity 1, N concurrent threads racing to hold the last
     * spot — exactly one hold row must survive every run.
     */
    @RepeatedTest(10)
    void exactly_one_hold_survives_N_concurrent_claims_on_a_capacity_one_session() throws InterruptedException {
        UUID sessionId = seedSession(1);
        int concurrency = 5;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(concurrency);
        AtomicInteger succeeded = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);

        for (int i = 0; i < concurrency; i++) {
            UUID paymentId = UUID.randomUUID();
            pool.submit(() -> {
                try {
                    start.await();
                    holdPort.holdAll(holdCommand(sessionId, paymentId), Instant.now().plusSeconds(1800));
                    succeeded.incrementAndGet();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException expectedCapacityTrip) {
                    // Losing the race is the expected outcome for every
                    // claimant but one.
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(holdRepository.findAll()).hasSize(1);
    }

    /**
     * TASK-3.6 (order: assignment committed first, hold attempted second).
     * Deterministic, not raced — a genuine concurrent race cannot pin
     * submission order to actual DB arrival order (two latch-released
     * threads reach InnoDB in JVM-scheduler order, not submission order),
     * so ordering is fixed the same way
     * {@code capacity_full_zero_inserts_and_exception} pins it for the
     * assignment-only adapter: complete the first writer synchronously,
     * then attempt the second.
     *
     * <p>This ordering is fully covered by Phase 3 alone: {@code
     * assertHold}'s step 2 ({@code countBySessionIdForUpdate}) already sees
     * the assignment's committed row, so a hold arriving second correctly
     * refuses.</p>
     */
    @Test
    void assignment_first_then_hold_is_refused() {
        UUID sessionId = seedSession(1);

        assignmentPort.assign(new CapacityAssignmentCommand(sessionId, UUID.randomUUID(), UUID.randomUUID()));

        assertThatThrownBy(() -> holdPort.holdAll(
            holdCommand(sessionId, UUID.randomUUID()), Instant.now().plusSeconds(1800)
        )).isInstanceOf(CapacityBelowAssignedException.class);

        assertThat(assignmentRepository.findAll()).hasSize(1);
        assertThat(holdRepository.findAll()).isEmpty();
    }

    /**
     * TASK-3.6 (order: hold committed first, assignment attempted second)
     * — the proposal's own High risk, tested directly and deterministically
     * for the same reason as the test above.
     *
     * <p><b>Currently oversells, on purpose left un-asserted as a
     * pass/fail.</b> Fixing this ordering is explicitly Phase 4's job
     * (today's unmodified {@code assertAssignment} — design step 4, task
     * 4.2, a later PR — does not yet count {@code activeHolds}, so
     * {@code assigned + 1 > capacity} reads false and the assignment
     * wrongly succeeds). Measured against THIS PR's hold writer with
     * today's unmodified {@code assertAssignment}: the oversell reproduces
     * on every run. This test intentionally documents that measured,
     * expected-until-Phase-4 state instead of asserting the still-missing
     * invariant, so PR3 stays green without silently deleting the proof.
     * Phase 4 flips the assertion below to expect the thrown exception.</p>
     */
    @Test
    void hold_first_then_assignment_currently_oversells_until_Phase_4() {
        UUID sessionId = seedSession(1);

        holdPort.holdAll(holdCommand(sessionId, UUID.randomUUID()), Instant.now().plusSeconds(1800));

        // NOT the desired end-state: recorded here as the measured proof of
        // the proposal's High risk (Phase 4 must flip this to a thrown
        // CapacityBelowAssignedException with zero assignment rows).
        assignmentPort.assign(new CapacityAssignmentCommand(sessionId, UUID.randomUUID(), UUID.randomUUID()));

        assertThat(holdRepository.findAll()).hasSize(1);
        assertThat(assignmentRepository.findAll())
            .as("Known oversell (proposal Risk table) — closed by Phase 4's assertAssignment invariant fix")
            .hasSize(1);
    }
}
