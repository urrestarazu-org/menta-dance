package com.menta.auth.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.auth.application.contract.AuthOutboxEventTypes;
import com.menta.auth.application.port.out.OutboxAppender;
import com.menta.shared.outbox.OutboxStatus;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * RED-GREEN discipline: this test references OutboxJpaAppender BEFORE 3.6
 * GREEN provides the impl, so it must not compile until the impl exists.
 *
 * Covers common-outbox spec scenarios:
 *   - "Login emite AuthUserLoggedIn post-commit" — adapter inserts with
 *     event_id ULID, status=PENDING, payload verbatim from caller.
 *   - "Inserción duplicada rechazada por la base" — adapter propagates
 *     UNIQUE constraint violations so the reconciler / caller can decide.
 *
 * Strict TDD: each assertion exercises the production code through the port
 * boundary and verifies both the column mapping AND the propagated exception.
 */
@ExtendWith(MockitoExtension.class)
class OutboxJpaAppenderTest {

    private static final String AGGREGATE_ID = "88888888-8888-8888-8888-888888888888";
    private static final String PAYLOAD = "{\"tokenVersion\":1}";
    private static final String EVENT_TYPE = AuthOutboxEventTypes.AUTH_USER_LOGGED_IN;
    private static final String EVENT_ID = "01H9X3F4Z9YJ7K5Q6T2R8V1N4Q";
    private static final Instant CREATED_AT = Instant.parse("2026-07-29T12:00:00Z");

    @Mock private com.menta.auth.infrastructure.persistence.repository.OutboxRowJpaRepository repository;
    @Mock private com.menta.auth.infrastructure.outbox.persistence.UlidGenerator ulidGenerator;
    @Mock private com.menta.auth.infrastructure.outbox.persistence.OutboxClock outboxClock;

    private OutboxJpaAppender appender;

    @BeforeEach
    void setUp() {
        when(ulidGenerator.next()).thenReturn(EVENT_ID);
        when(outboxClock.now()).thenReturn(CREATED_AT);
        appender = new OutboxJpaAppender(repository, ulidGenerator, outboxClock);
    }

    @Nested
    @DisplayName("Spec: Login emite AuthUserLoggedIn post-commit")
    class AppendsRow {

        @Test
        void inserts_a_pending_row_with_event_id_aggregate_id_payload() {
            doAnswer(inv -> inv.getArgument(0))
                .when(repository).save(any(
                    com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity.class));

            OutboxAppender port = appender;
            port.append(EVENT_TYPE, AGGREGATE_ID, PAYLOAD);

            ArgumentCaptor<
                com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity
            > captor = ArgumentCaptor.forClass(
                com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity.class
            );
            verify(repository, times(1)).save(captor.capture());

            com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity saved =
                captor.getValue();
            assertThat(saved.getEventId()).isEqualTo(EVENT_ID);
            assertThat(saved.getEventType()).isEqualTo(EVENT_TYPE);
            assertThat(saved.getAggregateId()).isEqualTo(AGGREGATE_ID);
            assertThat(saved.getPayload()).isEqualTo(PAYLOAD);
            assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(saved.getCreatedAt()).isEqualTo(CREATED_AT);
            assertThat(saved.getProcessedAt()).isNull();
            assertThat(saved.getAttempts()).isZero();
        }
    }

    @Nested
    @DisplayName("Spec: Inserción duplicada rechazada por la base")
    class DuplicateRejected {

        @Test
        void propagates_data_integrity_violation_so_caller_can_decide() {
            DataIntegrityViolationException fromDb = new DataIntegrityViolationException(
                "Duplicate entry for key 'uk_common_outbox_event_id'"
            );
            doThrow(fromDb)
                .when(repository).save(any(
                    com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity.class));

            OutboxAppender port = appender;
            assertThatThrownBy(() -> port.append(EVENT_TYPE, AGGREGATE_ID, PAYLOAD))
                .as("Adapter MUST surface UNIQUE constraint violations unchanged — "
                    + "idempotency policy lives at the caller / reconciler")
                .isSameAs(fromDb);
        }
    }
}
