package com.menta.app.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import com.menta.auth.infrastructure.persistence.repository.OutboxRowJpaRepository;
import com.menta.shared.outbox.OutboxStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

/**
 * RED-GREEN discipline: this test references OutboxBlacklistReconciler
 * BEFORE 3.8 GREEN provides the impl, so it must not compile until the
 * impl exists in :api:app/outbox.
 *
 * Spec scenarios covered (common-outbox):
 *   - "Reconciler procesa lote y marca COMPLETED"
 *   - "Redis caído marca FAILED con backoff"
 *   - "Crash entre commit y reconciler retoma"
 *
 * Strict TDD: every assertion exercises the production code through the
 * collaborator boundary (TokenBlacklistPort + JPA repository). Mock harness
 * drives real branch decisions (TTL math, failure-mode mapping, attempts
 * and last_error attribution after a red-failure or crash).
 */
@ExtendWith(MockitoExtension.class)
class OutboxBlacklistReconcilerTest {

    private static final int BATCH_SIZE = 100;
    private static final long ACCESS_TTL_SECONDS = 900L;
    private static final long BACKOFF_SECONDS = 30L;
    private static final int RETENTION_DAYS = 7;
    private static final Duration ACCESS_TTL = Duration.ofSeconds(ACCESS_TTL_SECONDS);

    @Mock private OutboxRowJpaRepository repository;
    @Mock private TokenBlacklistPort tokenBlacklistPort;

    private OutboxBlacklistReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new OutboxBlacklistReconciler(
            repository, tokenBlacklistPort, BATCH_SIZE, ACCESS_TTL_SECONDS,
            BACKOFF_SECONDS, RETENTION_DAYS
        );
    }

    @Nested
    @DisplayName("Spec: Reconciler procesa lote y marca COMPLETED")
    class ProcessBatch {

        @Test
        void projects_each_pending_row_to_redis_and_marks_completed() {
            OutboxRowJpaEntity a = pendingRow(101L, "auth.AuthUserLoggedIn", "jti-a");
            OutboxRowJpaEntity b = pendingRow(102L, "auth.AuthUserLoggedIn", "jti-b");
            OutboxRowJpaEntity c = pendingRow(103L, "auth.UserLoggedOut", "jti-c");
            when(repository.findByStatusOrderByIdAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(a, b, c));
            when(repository.save(any(OutboxRowJpaEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            reconciler.tick();

            verify(tokenBlacklistPort, times(3)).blacklist(anyString(), any(Duration.class));

            ArgumentCaptor<OutboxRowJpaEntity> captor =
                ArgumentCaptor.forClass(OutboxRowJpaEntity.class);
            verify(repository, times(3)).save(captor.capture());

            for (OutboxRowJpaEntity saved : captor.getAllValues()) {
                assertThat(saved.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
                assertThat(saved.getProcessedAt()).isNotNull();
                assertThat(saved.getAttempts()).isZero();
                assertThat(saved.getLastError()).isNull();
            }
        }

        @Test
        void blacklist_projection_uses_aggregate_id_as_jti_with_access_ttl() {
            OutboxRowJpaEntity row = pendingRow(101L, "auth.AuthUserLoggedIn", "jti-xyz");
            when(repository.findByStatusOrderByIdAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(row));
            when(repository.save(any(OutboxRowJpaEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            reconciler.tick();

            verify(tokenBlacklistPort, times(1)).blacklist(eq("jti-xyz"), eq(ACCESS_TTL));
        }

        @Test
        void empty_batch_is_a_no_op() {
            when(repository.findByStatusOrderByIdAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of());

            reconciler.tick();

            verify(tokenBlacklistPort, times(0)).blacklist(anyString(), any(Duration.class));
            verify(repository, times(0)).save(any(OutboxRowJpaEntity.class));
        }
    }

    @Nested
    @DisplayName("Spec: Redis caído marca FAILED con backoff")
    class RedisDown {

        @Test
        void marks_failed_attempts_bumps_last_error_set_and_records_next_retry() {
            OutboxRowJpaEntity row = pendingRow(201L, "auth.AuthUserLoggedIn", "jti-bad");
            when(repository.findByStatusOrderByIdAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(row));
            when(repository.save(any(OutboxRowJpaEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            RuntimeException redisBlip = new RuntimeException("connection refused");
            doThrow(redisBlip).when(tokenBlacklistPort)
                .blacklist(anyString(), any(Duration.class));

            reconciler.tick();

            ArgumentCaptor<OutboxRowJpaEntity> captor =
                ArgumentCaptor.forClass(OutboxRowJpaEntity.class);
            verify(repository).save(captor.capture());
            OutboxRowJpaEntity saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(OutboxStatus.FAILED);
            assertThat(saved.getAttempts()).isEqualTo(1);
            assertThat(saved.getLastError()).contains("connection refused");
            assertThat(saved.getNextRetryAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Spec: Crash entre commit y reconciler retoma")
    class CrashResume {

        @Test
        void re_picks_still_pending_rows_on_next_tick() {
            // Simulate the crash scenario: a row was committed as PENDING
            // by :api:auth but never reached COMPLETED before a reconciler
            // tick crashed. The next tick MUST still see the row as
            // PENDING and process it again. UNIQUE constraints on the
            // producer side + Redis SET-with-TTL idempotency make this
            // safe.
            OutboxRowJpaEntity orphan = pendingRow(
                999L, "auth.AuthUserLoggedIn", "jti-orphan");
            when(repository.findByStatusOrderByIdAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(orphan));
            when(repository.save(any(OutboxRowJpaEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            reconciler.tick();

            verify(tokenBlacklistPort).blacklist(eq("jti-orphan"), any(Duration.class));
            ArgumentCaptor<OutboxRowJpaEntity> captor =
                ArgumentCaptor.forClass(OutboxRowJpaEntity.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(OutboxStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("Spec: Cleanup borra COMPLETED antiguos")
    class RetentionCleanup {

        @Test
        void deletes_completed_events_older_than_retention_period() {
            when(repository.deleteByStatusAndProcessedAtBefore(
                eq(OutboxStatus.COMPLETED), any(Instant.class)))
                .thenReturn(42L);

            reconciler.cleanupProcessedEvents();

            ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(repository).deleteByStatusAndProcessedAtBefore(
                eq(OutboxStatus.COMPLETED), cutoffCaptor.capture());

            Instant cutoff = cutoffCaptor.getValue();
            Instant expectedCutoff = Instant.now().minus(Duration.ofDays(RETENTION_DAYS));
            // Allow 1 second tolerance for test execution time
            assertThat(cutoff).isBetween(
                expectedCutoff.minusSeconds(1),
                expectedCutoff.plusSeconds(1));
        }

        @Test
        void does_not_delete_failed_events() {
            when(repository.deleteByStatusAndProcessedAtBefore(
                eq(OutboxStatus.COMPLETED), any(Instant.class)))
                .thenReturn(0L);

            reconciler.cleanupProcessedEvents();

            // Only COMPLETED status is passed, not FAILED
            verify(repository).deleteByStatusAndProcessedAtBefore(
                eq(OutboxStatus.COMPLETED), any(Instant.class));
        }
    }

    // ---- helpers ----

    private static OutboxRowJpaEntity pendingRow(long id, String eventType, String aggregateId) {
        OutboxRowJpaEntity row = new OutboxRowJpaEntity(
            "01H9X3F4Z9YJ7K5Q6T2R8V1N4P", eventType, aggregateId,
            "{}", OutboxStatus.PENDING, 0, null, null,
            Instant.parse("2026-07-29T10:00:00Z"), null
        );
        // Force PK so test assertions / captors can track row identity.
        row.forceId(id);
        return row;
    }
}
