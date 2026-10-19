package com.menta.billing.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.application.contract.BillingOutboxEventTypes;
import com.menta.billing.application.port.out.BillingOutboxAppenderPort;
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
 * RED-GREEN: every test references {@link BillingOutboxAppenderPort} BEFORE the
 * concrete {@link BillingOutboxAppender} exists, so compilation forces the
 * impl to be written to satisfy the contracts the spec derives from
 * {@code presential-purchase-fulfillment}.
 *
 * <p>The appender is the SOLE writer to {@code common_outbox_events} on the
 * billing side, counterpart to {@code api:auth}'s {@code OutboxJpaAppender}.
 * Mirrors the same shape: caller-supplied payload strings, ULID generated
 * here, status starts {@code PENDING}, attempts starts at 0.</p>
 */
@ExtendWith(MockitoExtension.class)
class BillingOutboxAppenderTest {

    private static final String PAYMENT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String EVENT_TYPE = BillingOutboxEventTypes.PHYSICAL_PAYMENT_COMPLETED;
    private static final String PAYLOAD = "{\"paymentId\":\"11111111-1111-1111-1111-111111111111\"}";
    private static final String EVENT_ID = "01H9X3F4Z9YJ7K5Q6T2R8V1N4Q";
    private static final Instant CREATED_AT = Instant.parse("2026-08-24T13:00:00Z");

    @Mock private BillingOutboxRowJpaRepository repository;
    @Mock private BillingUlidGenerator ulidGenerator;
    @Mock private BillingOutboxClock outboxClock;

    private BillingOutboxAppender appender;

    @BeforeEach
    void setUp() {
        when(ulidGenerator.next()).thenReturn(EVENT_ID);
        when(outboxClock.now()).thenReturn(CREATED_AT);
        appender = new BillingOutboxAppender(repository, ulidGenerator, outboxClock);
    }

    @Nested
    @DisplayName("Spec: Completed physical payment appends one outbox row")
    class AppendsRow {

        @Test
        void persists_a_pending_row_with_event_id_aggregate_id_payload_verbatim() {
            when(repository.save(any(BillingOutboxRowJpaEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            BillingOutboxAppenderPort port = appender;
            port.append(EVENT_TYPE, PAYMENT_ID, PAYLOAD);

            ArgumentCaptor<BillingOutboxRowJpaEntity> captor = ArgumentCaptor.forClass(
                BillingOutboxRowJpaEntity.class
            );
            verify(repository, times(1)).save(captor.capture());

            BillingOutboxRowJpaEntity saved = captor.getValue();
            assertThat(saved.getEventId()).isEqualTo(EVENT_ID);
            assertThat(saved.getEventType()).isEqualTo(EVENT_TYPE);
            assertThat(saved.getAggregateId()).isEqualTo(PAYMENT_ID);
            assertThat(saved.getPayload()).isEqualTo(PAYLOAD);
            assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(saved.getCreatedAt()).isEqualTo(CREATED_AT);
            assertThat(saved.getProcessedAt()).isNull();
            assertThat(saved.getAttempts()).isZero();
            assertThat(saved.getLastError()).isNull();
            assertThat(saved.getNextRetryAt()).isNull();
        }
    }

    @Nested
    @DisplayName("Spec: Inserción duplicada rechazada por la base")
    class DuplicateRejected {

        @Test
        void propagates_data_integrity_violation_so_publisher_can_decide() {
            DataIntegrityViolationException fromDb = new DataIntegrityViolationException(
                "Duplicate entry for key 'uk_common_outbox_event_id'"
            );
            doThrow(fromDb)
                .when(repository).save(any(BillingOutboxRowJpaEntity.class));

            assertThatThrownBy(() -> appender.append(EVENT_TYPE, PAYMENT_ID, PAYLOAD))
                .as("Adapter MUST surface UNIQUE constraint violations unchanged — "
                    + "idempotency policy lives at the caller / reconciler")
                .isSameAs(fromDb);
        }
    }
}
