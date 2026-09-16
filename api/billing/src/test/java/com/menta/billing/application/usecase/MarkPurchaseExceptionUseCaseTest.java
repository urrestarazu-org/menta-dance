package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.application.contract.BillingOutboxEventTypes;
import com.menta.billing.application.port.out.BillingOutboxAppenderPort;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.application.port.out.PurchaseRepository;
import com.menta.billing.domain.exception.IllegalPurchaseStateTransitionException;
import com.menta.billing.domain.exception.PaymentNotFoundException;
import com.menta.billing.domain.model.FulfillmentStatus;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentStatus;
import com.menta.billing.domain.model.PaymentTarget;
import com.menta.billing.domain.model.Purchase;
import com.menta.billing.domain.model.Reason;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * RED-GREEN: every assertion references the new
 * {@link MarkPurchaseExceptionUseCase}. Maps design §4.2 strictly:
 *
 * <ul>
 *   <li>PENDING_FULFILLMENT → EXCEPTION — accepted (spec scenario "Capacity
 *       invariant trips — Purchase flips to EXCEPTION"), and D1 requires
 *       exactly one {@code billing.PurchaseExceptioned} outbox append,
 *       inside the same transaction as the {@code save}.</li>
 *   <li>ASSIGNED → EXCEPTION — refused (ADR-0028 §Decisión: once assigned,
 *       no going back); zero appends.</li>
 *   <li>EXCEPTION → EXCEPTION — idempotent no-op; zero appends.</li>
 *   <li>missing — throws PaymentNotFoundException; zero appends.</li>
 * </ul>
 */
class MarkPurchaseExceptionUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-24T13:00:00Z");
    private static final PaymentId PAYMENT_ID = PaymentId.of(UUID.fromString("55555555-5555-5555-5555-555555555555"));
    private static final String SESSION_ID = "33333333-3333-3333-3333-333333333333";
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Money AMOUNT = Money.of(new BigDecimal("1500.00"), "ARS");

    private PurchaseRepository purchaseRepository;
    private PaymentRepository paymentRepository;
    private BillingOutboxAppenderPort outboxAppender;
    private Clock clock;
    private MarkPurchaseExceptionUseCase useCase;

    @BeforeEach
    void setUp() {
        purchaseRepository = mock(PurchaseRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        outboxAppender = mock(BillingOutboxAppenderPort.class);
        clock = mock(Clock.class);
        when(clock.now()).thenReturn(NOW);
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(inv -> inv.getArgument(0));
        useCase = new MarkPurchaseExceptionUseCase(purchaseRepository, paymentRepository, outboxAppender, clock);
    }

    private static Payment physicalPayment() {
        return new Payment(
            PAYMENT_ID, USER_ID, "mp-1", AMOUNT, "ext-1", "merchant-1",
            new PaymentTarget.Physical(SESSION_ID), new PaymentStatus.Completed(NOW), NOW
        );
    }

    @Nested
    @DisplayName("Spec: Capacity invariant trips — Purchase flips to EXCEPTION")
    class PendingToException {

        @Test
        void flips_PENDING_FULFILLMENT_to_EXCEPTION_and_persists() {
            Purchase pending = Purchase.pendingFulfillment(PAYMENT_ID, java.util.List.of(SESSION_ID));
            when(purchaseRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(pending));
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(physicalPayment()));

            useCase.markException(PAYMENT_ID, Reason.CAPACITY_BELOW_ASSIGNED);

            ArgumentCaptor<Purchase> captor = ArgumentCaptor.forClass(Purchase.class);
            verify(purchaseRepository, times(1)).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(FulfillmentStatus.EXCEPTION);
            assertThat(captor.getValue().getPaymentId()).isEqualTo(PAYMENT_ID);
        }

        @Test
        void appends_exactly_one_purchase_exceptioned_outbox_event_after_saving() {
            Purchase pending = Purchase.pendingFulfillment(PAYMENT_ID, java.util.List.of(SESSION_ID));
            when(purchaseRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(pending));
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(physicalPayment()));

            useCase.markException(PAYMENT_ID, Reason.CAPACITY_BELOW_ASSIGNED);

            ArgumentCaptor<String> eventType = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> aggregateId = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
            verify(outboxAppender, times(1))
                .append(eventType.capture(), aggregateId.capture(), payload.capture());

            assertThat(eventType.getValue()).isEqualTo(BillingOutboxEventTypes.PURCHASE_EXCEPTIONED);
            assertThat(aggregateId.getValue()).isEqualTo(PAYMENT_ID.getValue().toString());
            assertThat(payload.getValue())
                .contains("\"paymentId\":\"" + PAYMENT_ID.getValue() + "\"")
                .contains("\"purchaseId\":\"" + pending.getId() + "\"")
                .contains("\"userId\":\"" + USER_ID + "\"")
                .contains("\"reason\":\"CAPACITY_BELOW_ASSIGNED\"")
                .contains("\"occurredAt\":");
        }
    }

    @Nested
    @DisplayName("ADR-0028 §Decisión: once assigned, no rolling back to EXCEPTION")
    class AssignedCannotFlip {

        @Test
        void assigned_purchase_refuses_to_move_to_EXCEPTION() {
            Purchase assigned = Purchase.pendingFulfillment(PAYMENT_ID, java.util.List.of(SESSION_ID)).assigned();
            when(purchaseRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(assigned));

            assertThatThrownBy(() ->
                useCase.markException(PAYMENT_ID, Reason.CAPACITY_BELOW_ASSIGNED)
            ).isInstanceOf(IllegalPurchaseStateTransitionException.class);

            verify(purchaseRepository, never()).save(any(Purchase.class));
            verify(outboxAppender, never()).append(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Idempotency guard — re-applying EXCEPTION on EXCEPTION is a no-op")
    class ExceptionIsIdempotent {

        @Test
        void re_marking_an_EXCEPTION_purchase_does_not_save_a_row() {
            Purchase exception = Purchase.pendingFulfillment(PAYMENT_ID, java.util.List.of(SESSION_ID)).exception();
            when(purchaseRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(exception));

            useCase.markException(PAYMENT_ID, Reason.UNIQUE_COLLISION);

            verify(purchaseRepository, never()).save(any(Purchase.class));
            verify(outboxAppender, never()).append(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Mis-use guard — paymentId without a row is loud")
    class MissingPurchase {

        @Test
        void throws_PaymentNotFoundException_when_no_row_resolves() {
            when(purchaseRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                useCase.markException(PAYMENT_ID, Reason.HOLD_EXPIRED)
            ).isInstanceOf(PaymentNotFoundException.class);

            verify(outboxAppender, never()).append(any(), any(), any());
        }
    }
}
