package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.menta.billing.application.contract.BillingOutboxEventTypes;
import com.menta.billing.application.port.out.BillingOutboxAppenderPort;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentStatus;
import com.menta.billing.domain.model.PaymentTarget;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * RED-GREEN: every assertion references
 * {@link PublishPhysicalPaymentCompletedUseCase}, the producer side of the
 * presential flow.
 *
 * <p>Spec scenarios exercised:
 * <ul>
 *   <li>"Completed physical payment appends one outbox row" — happy path
 *       via {@link TransactionSynchronization#afterCommit()}.</li>
 *   <li>"Completed virtual payment publishes no physical event" — the
 *       synchronization MUST NOT be registered for a non-physical target.</li>
 *   <li>"Rolled-back payment leaves empty outbox and empty purchases" —
 *       proves registration alone is not enough: only {@code afterCommit}
 *       fires the appender. Spring drops the synchronization on rollback,
 *       and unit-level proof of the same contract: never invoking
 *       {@code afterCommit()} means the appender is never called.</li>
 * </ul>
 */
class PublishPhysicalPaymentCompletedUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-24T13:00:00Z");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Money AMOUNT = Money.of(new BigDecimal("1500.00"), "ARS");
    private static final PaymentId PAYMENT_ID = PaymentId.generate();
    private static final UUID SESSION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private BillingOutboxAppenderPort outboxAppender;
    private PublishPhysicalPaymentCompletedUseCase useCase;

    @BeforeEach
    void setUp() {
        outboxAppender = mock(BillingOutboxAppenderPort.class);
        useCase = new PublishPhysicalPaymentCompletedUseCase(outboxAppender);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static Payment physicalPayment(PaymentStatus status) {
        return new Payment(
            PAYMENT_ID, USER_ID, "mp-1", AMOUNT, "ext-1", "merchant-1",
            new PaymentTarget.Physical(SESSION_ID.toString()), status, NOW
        );
    }

    @Nested
    @DisplayName("Spec scenario: Completed physical payment appends one outbox row")
    class AfterCommitHappy {

        @Test
        void registers_after_commit_synchronization_that_invokes_the_outbox_appender() {
            useCase.handle(physicalPayment(new PaymentStatus.Completed(NOW)));

            List<TransactionSynchronization> syncs =
                TransactionSynchronizationManager.getSynchronizations();
            assertThat(syncs).hasSize(1);

            TransactionSynchronization sync = syncs.get(0);
            sync.afterCommit();

            ArgumentCaptor<String> eventType = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> aggregateId = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
            verify(outboxAppender, times(1))
                .append(eventType.capture(), aggregateId.capture(), payload.capture());

            assertThat(eventType.getValue()).isEqualTo(BillingOutboxEventTypes.PHYSICAL_PAYMENT_COMPLETED);
            assertThat(aggregateId.getValue()).isEqualTo(PAYMENT_ID.getValue().toString());
            assertThat(payload.getValue())
                .contains("\"paymentId\":\"" + PAYMENT_ID.getValue() + "\"")
                .contains("\"providerPaymentId\":\"mp-1\"")
                .contains("\"externalReference\":\"ext-1\"")
                .contains("\"merchantAccountId\":\"merchant-1\"")
                .contains("\"targetReference\":\"" + SESSION_ID + "\"")
                .contains("\"amount\":1500.00")
                .contains("\"currency\":\"ARS\"");
        }
    }

    @Nested
    @DisplayName("Spec: Completed virtual payment publishes no physical event")
    class VirtualPaymentIgnored {

        @Test
        void does_not_register_synchronization_when_target_is_virtual() {
            Payment virtual = new Payment(
                PaymentId.generate(), USER_ID, "mp-virtual", AMOUNT, "ext-v", "merchant-1",
                new PaymentTarget.Virtual("plan-1"),
                new PaymentStatus.Completed(NOW), NOW
            );

            useCase.handle(virtual);

            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
            verify(outboxAppender, never()).append(any(), any(), any());
        }

        @Test
        void does_not_register_synchronization_when_target_is_virtual_pending() {
            Payment virtualPending = new Payment(
                PaymentId.generate(), USER_ID, null, AMOUNT, "ext-v", "merchant-1",
                new PaymentTarget.Virtual("plan-1"),
                new PaymentStatus.AwaitingProvider(), NOW
            );

            useCase.handle(virtualPending);

            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Spec: Rolled-back payment leaves empty outbox and empty purchases")
    class RolledBackPaymentNoEvent {

        @Test
        void after_commit_must_be_called_explicitly_or_no_outbox_row_appears() {
            useCase.handle(physicalPayment(new PaymentStatus.Completed(NOW)));

            List<TransactionSynchronization> syncs =
                TransactionSynchronizationManager.getSynchronizations();
            assertThat(syncs).hasSize(1);

            // Drop the synchronization without firing afterCommit — this mimics
            // Spring's own rollback behavior: the registered sync never fires.
            TransactionSynchronizationManager.clearSynchronization();

            verify(outboxAppender, never()).append(any(), any(), any());
        }

        @Test
        void publishes_nothing_for_a_non_completed_physical_payment() {
            useCase.handle(physicalPayment(new PaymentStatus.AwaitingProvider()));

            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
            verify(outboxAppender, never()).append(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Reason enum codifies the three documented residual modes")
    class ReasonEnumCoverage {

        @Test
        void exposes_the_documented_residual_reasons_per_design_section_4_2() {
            assertThat(com.menta.billing.domain.model.Reason.values())
                .extracting(Enum::name)
                .containsExactlyInAnyOrder(
                    "CAPACITY_BELOW_ASSIGNED", "UNIQUE_COLLISION",
                    "HOLD_EXPIRED", "COVERAGE_CHANGED", "TARGET_NOT_SCHEDULED"
                );
        }
    }

    @Nested
    @DisplayName("Idempotent re-delivery surfacing existing completion")
    class IdempotentSurface {

        @Test
        void registers_a_fresh_after_commit_again_on_a_second_handle_call() {
            Payment payment = physicalPayment(new PaymentStatus.Completed(NOW));
            useCase.handle(payment);
            useCase.handle(payment);

            List<TransactionSynchronization> syncs =
                TransactionSynchronizationManager.getSynchronizations();
            assertThat(syncs).hasSize(2);

            syncs.forEach(TransactionSynchronization::afterCommit);

            verify(outboxAppender, times(2))
                .append(eq(BillingOutboxEventTypes.PHYSICAL_PAYMENT_COMPLETED),
                    eq(PAYMENT_ID.getValue().toString()), any());
        }
    }
}
