package com.menta.app.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.auth.application.contract.AuthOutboxEventTypes;
import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import com.menta.auth.infrastructure.persistence.repository.OutboxRowJpaRepository;
import com.menta.shared.outbox.OutboxStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * RED contract for task 2.6. The legacy worker blacklists every event; the
 * replacement must resolve exactly one handler by event type before applying
 * any side effect.
 */
@ExtendWith(MockitoExtension.class)
class OutboxReconciliationWorkerDispatchTest {

    private static final long BACKOFF_SECONDS = 30L;
    private static final int MAX_ATTEMPTS = 5;
    private static final String SECRET_PAYLOAD = "{\"activationTokenId\":\"secret-token-id\"}";

    @Mock private OutboxRowJpaRepository repository;
    @Mock private OutboxEventHandler blacklistHandler;
    @Mock private OutboxEventHandler activationHandler;
    @Mock private OutboxEventHandler anotherActivationHandler;

    @BeforeEach
    void saveRows() {
        lenient().when(repository.save(any(OutboxRowJpaEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void dispatches_a_blacklist_event_to_its_single_declared_handler() {
        OutboxRowJpaEntity row = pendingRow(AuthOutboxEventTypes.REFRESH_REVOKED, "family-id");
        when(blacklistHandler.supports(AuthOutboxEventTypes.REFRESH_REVOKED)).thenReturn(true);
        when(activationHandler.supports(AuthOutboxEventTypes.REFRESH_REVOKED)).thenReturn(false);

        OutboxReconciliationWorker worker = workerWith(blacklistHandler, activationHandler);

        assertThat(worker.process(row)).isEqualTo(OutboxProcessResult.COMPLETED);

        verify(blacklistHandler).handle(row);
        verify(activationHandler, never()).handle(any(OutboxRowJpaEntity.class));
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
    }

    @Test
    void dispatches_an_activation_event_to_its_single_declared_handler_without_blacklisting() {
        OutboxRowJpaEntity row = pendingRow(
            AuthOutboxEventTypes.ACCOUNT_ACTIVATION_REQUESTED, "activation-token-id"
        );
        when(blacklistHandler.supports(AuthOutboxEventTypes.ACCOUNT_ACTIVATION_REQUESTED))
            .thenReturn(false);
        when(activationHandler.supports(AuthOutboxEventTypes.ACCOUNT_ACTIVATION_REQUESTED))
            .thenReturn(true);

        OutboxReconciliationWorker worker = workerWith(blacklistHandler, activationHandler);

        assertThat(worker.process(row)).isEqualTo(OutboxProcessResult.COMPLETED);

        verify(activationHandler).handle(row);
        verify(blacklistHandler, never()).handle(any(OutboxRowJpaEntity.class));
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
    }

    @Test
    void fails_with_backoff_and_a_sanitized_diagnostic_when_no_handler_supports_the_event() {
        OutboxRowJpaEntity row = pendingRow("unknown.Event", "private-aggregate-id");
        when(blacklistHandler.supports("unknown.Event")).thenReturn(false);
        when(activationHandler.supports("unknown.Event")).thenReturn(false);

        OutboxReconciliationWorker worker = workerWith(blacklistHandler, activationHandler);

        assertThat(worker.process(row)).isEqualTo(OutboxProcessResult.DISPATCH_FAILED);

        verify(blacklistHandler, never()).handle(any(OutboxRowJpaEntity.class));
        verify(activationHandler, never()).handle(any(OutboxRowJpaEntity.class));
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getNextRetryAt()).isNotNull();
        assertThat(row.getLastError()).isEqualTo("No handler registered for event type: unknown.Event");
        assertThat(row.getLastError())
            .doesNotContain(SECRET_PAYLOAD, "private-aggregate-id", "activation-token-id");
    }

    @Test
    void fails_with_backoff_without_side_effect_when_multiple_handlers_support_the_event() {
        OutboxRowJpaEntity row = pendingRow(
            AuthOutboxEventTypes.ACCOUNT_ACTIVATION_REQUESTED, "activation-token-id"
        );
        when(activationHandler.supports(AuthOutboxEventTypes.ACCOUNT_ACTIVATION_REQUESTED))
            .thenReturn(true);
        when(anotherActivationHandler.supports(AuthOutboxEventTypes.ACCOUNT_ACTIVATION_REQUESTED))
            .thenReturn(true);

        OutboxReconciliationWorker worker = workerWith(activationHandler, anotherActivationHandler);

        assertThat(worker.process(row)).isEqualTo(OutboxProcessResult.DISPATCH_FAILED);

        verify(activationHandler, never()).handle(any(OutboxRowJpaEntity.class));
        verify(anotherActivationHandler, never()).handle(any(OutboxRowJpaEntity.class));
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getNextRetryAt()).isNotNull();
        assertThat(row.getLastError()).isEqualTo(
            "Multiple handlers registered for event type: auth.AccountActivationRequested"
        );
    }

    @Test
    void reports_a_dispatch_failure_when_the_handler_rejects_the_row_as_undispatchable() {
        OutboxRowJpaEntity row = pendingRow(AuthOutboxEventTypes.REFRESH_REVOKED, "family-id");
        when(blacklistHandler.supports(AuthOutboxEventTypes.REFRESH_REVOKED)).thenReturn(true);
        doThrow(new OutboxDispatchException("Unreadable outbox payload"))
            .when(blacklistHandler).handle(row);

        OutboxProcessResult result = workerWith(blacklistHandler).process(row);

        assertThat(result).isEqualTo(OutboxProcessResult.DISPATCH_FAILED);
        assertThat(result.blocksHeartbeat()).isFalse();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }

    @Test
    void reports_a_side_effect_failure_when_the_handler_throws_anything_else() {
        OutboxRowJpaEntity row = pendingRow(AuthOutboxEventTypes.REFRESH_REVOKED, "family-id");
        when(blacklistHandler.supports(AuthOutboxEventTypes.REFRESH_REVOKED)).thenReturn(true);
        doThrow(new RuntimeException("connection refused")).when(blacklistHandler).handle(row);

        OutboxProcessResult result = workerWith(blacklistHandler).process(row);

        assertThat(result).isEqualTo(OutboxProcessResult.SIDE_EFFECT_FAILED);
        assertThat(result.blocksHeartbeat()).isTrue();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(row.getAttempts()).isEqualTo(1);
    }

    @Test
    void completed_rows_do_not_block_the_heartbeat() {
        assertThat(OutboxProcessResult.COMPLETED.blocksHeartbeat()).isFalse();
    }

    @Test
    void keeps_retrying_with_backoff_while_attempts_stay_below_the_cap() {
        OutboxRowJpaEntity row = failedRow("unknown.Event", MAX_ATTEMPTS - 2);

        workerWith(blacklistHandler).process(row);

        assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(row.getAttempts()).isEqualTo(MAX_ATTEMPTS - 1);
        assertThat(row.getNextRetryAt()).isNotNull();
    }

    @Test
    void parks_the_row_as_dead_when_the_failed_attempt_reaches_the_cap() {
        OutboxRowJpaEntity row = failedRow("unknown.Event", MAX_ATTEMPTS - 1);

        OutboxProcessResult result = workerWith(blacklistHandler).process(row);

        assertThat(result).isEqualTo(OutboxProcessResult.DISPATCH_FAILED);
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.DEAD);
        assertThat(row.getAttempts()).isEqualTo(MAX_ATTEMPTS);
        assertThat(row.getNextRetryAt()).isNull();
        assertThat(row.getLastError()).isEqualTo("No handler registered for event type: unknown.Event");
    }

    @Test
    void parks_a_side_effect_failure_as_dead_at_the_cap_too() {
        OutboxRowJpaEntity row = failedRow(AuthOutboxEventTypes.REFRESH_REVOKED, MAX_ATTEMPTS - 1);
        when(blacklistHandler.supports(AuthOutboxEventTypes.REFRESH_REVOKED)).thenReturn(true);
        doThrow(new RuntimeException("connection refused")).when(blacklistHandler).handle(row);

        OutboxProcessResult result = workerWith(blacklistHandler).process(row);

        assertThat(result).isEqualTo(OutboxProcessResult.SIDE_EFFECT_FAILED);
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.DEAD);
        assertThat(row.getNextRetryAt()).isNull();
    }

    @Test
    void a_legacy_row_of_a_removed_event_type_with_thousands_of_attempts_ends_dead() {
        // #672: databases already hold auth.AuthUserLoggedIn / auth.RefreshRotated rows that
        // looped for thousands of attempts. The first retry after the fix must close them.
        OutboxRowJpaEntity login = failedRow("auth.AuthUserLoggedIn", 9222);
        OutboxRowJpaEntity rotated = pendingRow("auth.RefreshRotated", "refresh-id");

        OutboxReconciliationWorker worker = workerWith(blacklistHandler);
        worker.process(login);
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            worker.process(rotated);
        }

        assertThat(login.getStatus()).isEqualTo(OutboxStatus.DEAD);
        assertThat(rotated.getStatus()).isEqualTo(OutboxStatus.DEAD);
        assertThat(rotated.getAttempts()).isEqualTo(MAX_ATTEMPTS);
    }

    private OutboxReconciliationWorker workerWith(OutboxEventHandler... handlers) {
        return new OutboxReconciliationWorker(
            repository, List.of(handlers), BACKOFF_SECONDS, MAX_ATTEMPTS);
    }

    private static OutboxRowJpaEntity failedRow(String eventType, int attempts) {
        return new OutboxRowJpaEntity(
            "01H9X3F4Z9YJ7K5Q6T2R8V1N4P", eventType, "aggregate-id",
            SECRET_PAYLOAD, OutboxStatus.FAILED, attempts, "previous error",
            Instant.parse("2026-07-29T10:00:00Z"), Instant.parse("2026-07-29T10:00:00Z"), null
        );
    }

    private static OutboxRowJpaEntity pendingRow(String eventType, String aggregateId) {
        return new OutboxRowJpaEntity(
            "01H9X3F4Z9YJ7K5Q6T2R8V1N4P", eventType, aggregateId,
            SECRET_PAYLOAD, OutboxStatus.PENDING, 0, null, null,
            Instant.parse("2026-07-29T10:00:00Z"), null
        );
    }
}
