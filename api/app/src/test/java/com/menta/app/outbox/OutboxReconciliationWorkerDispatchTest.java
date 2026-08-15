package com.menta.app.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private static final String SECRET_PAYLOAD = "{\"activationTokenId\":\"secret-token-id\"}";

    @Mock private OutboxRowJpaRepository repository;
    @Mock private OutboxEventHandler blacklistHandler;
    @Mock private OutboxEventHandler activationHandler;
    @Mock private OutboxEventHandler anotherActivationHandler;

    @BeforeEach
    void saveRows() {
        when(repository.save(any(OutboxRowJpaEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void dispatches_a_blacklist_event_to_its_single_declared_handler() {
        OutboxRowJpaEntity row = pendingRow(AuthOutboxEventTypes.AUTH_USER_LOGGED_IN, "jti-login");
        when(blacklistHandler.supports(AuthOutboxEventTypes.AUTH_USER_LOGGED_IN)).thenReturn(true);
        when(activationHandler.supports(AuthOutboxEventTypes.AUTH_USER_LOGGED_IN)).thenReturn(false);

        OutboxReconciliationWorker worker = workerWith(blacklistHandler, activationHandler);

        assertThat(worker.process(row)).isFalse();

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

        assertThat(worker.process(row)).isFalse();

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

        assertThat(worker.process(row)).isTrue();

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

        assertThat(worker.process(row)).isTrue();

        verify(activationHandler, never()).handle(any(OutboxRowJpaEntity.class));
        verify(anotherActivationHandler, never()).handle(any(OutboxRowJpaEntity.class));
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getNextRetryAt()).isNotNull();
        assertThat(row.getLastError()).isEqualTo(
            "Multiple handlers registered for event type: auth.AccountActivationRequested"
        );
    }

    private OutboxReconciliationWorker workerWith(OutboxEventHandler... handlers) {
        return new OutboxReconciliationWorker(repository, List.of(handlers), BACKOFF_SECONDS);
    }

    private static OutboxRowJpaEntity pendingRow(String eventType, String aggregateId) {
        return new OutboxRowJpaEntity(
            "01H9X3F4Z9YJ7K5Q6T2R8V1N4P", eventType, aggregateId,
            SECRET_PAYLOAD, OutboxStatus.PENDING, 0, null, null,
            Instant.parse("2026-07-29T10:00:00Z"), null
        );
    }
}
