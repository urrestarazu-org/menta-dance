package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.application.port.out.PurchaseRepository;
import com.menta.billing.domain.exception.IllegalPurchaseStateTransitionException;
import com.menta.billing.domain.exception.PaymentNotFoundException;
import com.menta.billing.domain.model.FulfillmentStatus;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Purchase;
import com.menta.billing.domain.model.Reason;
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
 *       invariant trips — Purchase flips to EXCEPTION").</li>
 *   <li>ASSIGNED → EXCEPTION — refused (ADR-0028 §Decisión: once assigned,
 *       no going back).</li>
 *   <li>EXCEPTION → EXCEPTION — idempotent no-op.</li>
 *   <li>missing — throws PaymentNotFoundException.</li>
 * </ul>
 */
class MarkPurchaseExceptionUseCaseTest {

    private static final PaymentId PAYMENT_ID = PaymentId.of(UUID.fromString("55555555-5555-5555-5555-555555555555"));
    private static final String SESSION_ID = "33333333-3333-3333-3333-333333333333";

    private PurchaseRepository purchaseRepository;
    private MarkPurchaseExceptionUseCase useCase;

    @BeforeEach
    void setUp() {
        purchaseRepository = mock(PurchaseRepository.class);
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(inv -> inv.getArgument(0));
        useCase = new MarkPurchaseExceptionUseCase(purchaseRepository);
    }

    @Nested
    @DisplayName("Spec: Capacity invariant trips — Purchase flips to EXCEPTION")
    class PendingToException {

        @Test
        void flips_PENDING_FULFILLMENT_to_EXCEPTION_and_persists() {
            Purchase pending = Purchase.pendingFulfillment(PAYMENT_ID, SESSION_ID);
            when(purchaseRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(pending));

            useCase.markException(PAYMENT_ID, Reason.CAPACITY_BELOW_ASSIGNED);

            ArgumentCaptor<Purchase> captor = ArgumentCaptor.forClass(Purchase.class);
            verify(purchaseRepository, times(1)).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(FulfillmentStatus.EXCEPTION);
            assertThat(captor.getValue().getPaymentId()).isEqualTo(PAYMENT_ID);
        }
    }

    @Nested
    @DisplayName("ADR-0028 §Decisión: once assigned, no rolling back to EXCEPTION")
    class AssignedCannotFlip {

        @Test
        void assigned_purchase_refuses_to_move_to_EXCEPTION() {
            Purchase assigned = Purchase.pendingFulfillment(PAYMENT_ID, SESSION_ID).assigned();
            when(purchaseRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(assigned));

            assertThatThrownBy(() ->
                useCase.markException(PAYMENT_ID, Reason.CAPACITY_BELOW_ASSIGNED)
            ).isInstanceOf(IllegalPurchaseStateTransitionException.class);

            verify(purchaseRepository, never()).save(any(Purchase.class));
        }
    }

    @Nested
    @DisplayName("Idempotency guard — re-applying EXCEPTION on EXCEPTION is a no-op")
    class ExceptionIsIdempotent {

        @Test
        void re_marking_an_EXCEPTION_purchase_does_not_save_a_row() {
            Purchase exception = Purchase.pendingFulfillment(PAYMENT_ID, SESSION_ID).exception();
            when(purchaseRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(exception));

            useCase.markException(PAYMENT_ID, Reason.UNIQUE_COLLISION);

            verify(purchaseRepository, never()).save(any(Purchase.class));
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
        }
    }
}
