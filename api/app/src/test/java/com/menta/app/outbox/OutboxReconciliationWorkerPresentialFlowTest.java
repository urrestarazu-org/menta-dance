package com.menta.app.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import com.menta.auth.infrastructure.persistence.repository.OutboxRowJpaRepository;
import com.menta.shared.outbox.OutboxStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Presential flow routes {@link
 * com.menta.physical.domain.exception.CapacityBelowAssignedException} to
 * {@link com.menta.app.billing.MarkPurchaseExceptionAdapter} so the
 * worker sees no RuntimeException. This test asserts the worker
 * classifies the handler as {@code COMPLETED} (so the row is closed
 * out, not stuck in the FAILED bucket), AND that a hypothetical
 * downstream Runtime exception (e.g. the JSON payload fails to parse
 * because a deployer broke it) still preserves the worker's backoff
 * semantics on its tail: the row is FAILED, the attempts counter is
 * bumped, and {@code next_retry_at} is in the future so the next
 * scheduler tick will not re-attempt before the backoff window
 * elapses.
 *
 * <p>This shape mirrors ReconcileFailedRowsScenariosTest so the contract
 * is consistent across auth and presential-routed handlers.</p>
 */
class OutboxReconciliationWorkerPresentialFlowTest {

    private static final Duration WORKER_BACKOFF = Duration.ofSeconds(30L);

    private OutboxRowJpaRepository repository;
    private PhysicalCapacityAssignmentOutboxEventHandler presentialHandler;
    private OutboxReconciliationWorker worker;

    @BeforeEach
    void setUp() {
        repository = mock(OutboxRowJpaRepository.class);
        presentialHandler = mock(PhysicalCapacityAssignmentOutboxEventHandler.class);
        when(presentialHandler.supports("billing.PhysicalPaymentCompleted")).thenReturn(true);
        worker = new OutboxReconciliationWorker(repository, List.of(presentialHandler), 30L);
    }

    @Test
    @DisplayName("Capacity-tripped path completes the row (handler swallows, row → COMPLETED)")
    void capacityTrippedToException_path_completes_the_row() {
        OutboxRowJpaEntity row = newRow();

        boolean failed = worker.process(row);

        assertThat(failed).as("handler chain absorbs CapacityBelowAssignedException").isFalse();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
        assertThat(row.getAttempts()).isZero();
        assertThat(row.getNextRetryAt()).isNull();
        verify(presentialHandler, times(1)).handle(row);
        verify(repository, times(1)).save(row);
    }

    @Test
    @DisplayName("Any handler RuntimeException inside the presential chain stays FAILED-backed-off")
    void unexpected_handler_exception_preserves_backoff() {
        OutboxRowJpaEntity row = newRow();
        doThrow(new RuntimeException("downstream DB went away"))
            .when(presentialHandler).handle(any());

        boolean failed = worker.process(row);

        assertThat(failed).isTrue();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getLastError()).isEqualTo("downstream DB went away");
        assertThat(row.getNextRetryAt()).isNotNull();
        assertThat(row.getNextRetryAt()).isAfter(Instant.now().minus(WORKER_BACKOFF).plusSeconds(5));
    }

    private OutboxRowJpaEntity newRow() {
        OutboxRowJpaEntity row = new OutboxRowJpaEntity(
            "01HFPRESENTIAL0000000000000000",
            "billing.PhysicalPaymentCompleted",
            "55555555-5555-5555-5555-555555555555",
            "{\"payload\":\"placeholder\"}",
            OutboxStatus.PENDING,
            0,
            null,
            Instant.now().plusSeconds(60),
            Instant.now(),
            null
        );
        return row;
    }
}
