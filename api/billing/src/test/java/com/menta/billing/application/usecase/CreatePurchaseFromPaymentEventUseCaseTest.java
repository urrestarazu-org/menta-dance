package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.application.port.out.PurchaseRepository;
import com.menta.billing.domain.exception.PaymentNotFoundException;
import com.menta.billing.domain.exception.PurchaseNotFoundException;
import com.menta.billing.domain.model.FulfillmentStatus;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Purchase;
import com.menta.shared.billing.PaymentCompletedOutboxPayload;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * RED-GREEN: every assertion references the new
 * {@link CreatePurchaseFromPaymentEventUseCase} the spec demands (spec
 * scenarios "First-time event creates a PENDING_FULFILLMENT purchase" and
 * "Re-delivery with same payment_id is idempotent"; design §5.4).
 */
class CreatePurchaseFromPaymentEventUseCaseTest {

    private static final UUID PAYMENT_UUID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final PaymentId PAYMENT_ID = PaymentId.of(PAYMENT_UUID);
    private static final String SESSION_ID = "33333333-3333-3333-3333-333333333333";
    private static final String SESSION_REF = SESSION_ID;

    private PurchaseRepository purchaseRepository;
    private CreatePurchaseFromPaymentEventUseCase useCase;

    @BeforeEach
    void setUp() {
        purchaseRepository = mock(PurchaseRepository.class);
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(inv -> inv.getArgument(0));
        useCase = new CreatePurchaseFromPaymentEventUseCase(purchaseRepository);
    }

    private static PaymentCompletedOutboxPayload payload() {
        return new PaymentCompletedOutboxPayload(
            PAYMENT_UUID, "mp-1", "ext-1", "merchant-1", SESSION_REF,
            BigDecimal.TEN, "ARS", Instant.parse("2026-08-24T13:00:00Z")
        );
    }

    @Nested
    @DisplayName("Spec: First-time event creates a PENDING_FULFILLMENT purchase")
    class FirstTimeDelivery {

        @Test
        void builds_pendingFulfillment_then_saves_when_no_existing_row() {
            when(purchaseRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.empty());

            Purchase result = useCase.createPurchaseFromPaymentEvent(payload());

            assertThat(result.getStatus()).isEqualTo(FulfillmentStatus.PENDING_FULFILLMENT);
            assertThat(result.getPaymentId()).isEqualTo(PAYMENT_ID);
            assertThat(result.getPhysicalSessionId()).isEqualTo(SESSION_REF);
            verify(purchaseRepository, times(1)).save(any(Purchase.class));
        }
    }

    @Nested
    @DisplayName("Spec: Re-delivery with same payment_id is idempotent")
    class IdempotentRedelivery {

        @Test
        void returns_existing_pendingFulfillment_without_saving() {
            Purchase existing = Purchase.pendingFulfillment(PAYMENT_ID, SESSION_REF);
            when(purchaseRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(existing));

            Purchase result = useCase.createPurchaseFromPaymentEvent(payload());

            assertThat(result).isSameAs(existing);
            verify(purchaseRepository, never()).save(any(Purchase.class));
        }

        @Test
        void returns_existing_assigned_without_saving() {
            Purchase existing = Purchase.pendingFulfillment(PAYMENT_ID, SESSION_REF).assigned();
            when(purchaseRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(existing));

            Purchase result = useCase.createPurchaseFromPaymentEvent(payload());

            assertThat(result).isSameAs(existing);
            verify(purchaseRepository, never()).save(any(Purchase.class));
        }

        @Test
        void rebuilds_when_existing_is_EXCEPTION_per_iso_double_recovery() {
            Purchase exception = Purchase.pendingFulfillment(PAYMENT_ID, SESSION_REF).exception();
            when(purchaseRepository.findByPaymentId(PAYMENT_ID))
                .thenReturn(Optional.of(exception));

            Purchase result = useCase.createPurchaseFromPaymentEvent(payload());

            assertThat(result.getStatus()).isEqualTo(FulfillmentStatus.PENDING_FULFILLMENT);
            verify(purchaseRepository, times(1)).save(any(Purchase.class));
        }
    }

    @Nested
    @DisplayName("V8 UNIQUE race on save() — second handler delivery within the same frame")
    class UniqueRace {

        @Test
        void recovers_by_re_fetching_when_save_throws_DataIntegrityViolationException() {
            Purchase existing = Purchase.pendingFulfillment(PAYMENT_ID, SESSION_REF);
            // First findByPaymentId() returns empty (handler-A inserted first
            // but not committed yet). Then save() raises DIV. Then the second
            // findByPaymentId() returns the now-committed row.
            when(purchaseRepository.findByPaymentId(PAYMENT_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
            doThrow(new DataIntegrityViolationException(
                "Duplicate entry for key 'uq_billing_purchases_payment_id'"))
                .when(purchaseRepository).save(any(Purchase.class));

            Purchase result = useCase.createPurchaseFromPaymentEvent(payload());

            assertThat(result).isSameAs(existing);
            verify(purchaseRepository, times(2)).findByPaymentId(PAYMENT_ID);
        }
    }

    @Nested
    @DisplayName("Wrong paymentId shape — guard rail for misuse")
    class Malformed {

        @Test
        void rejects_payload_whose_paymentId_does_not_resolve_to_PaymentId() {
            when(purchaseRepository.findByPaymentId(any(PaymentId.class)))
                .thenThrow(new PaymentNotFoundException(PAYMENT_ID));

            assertThatThrownBy(() -> useCase.createPurchaseFromPaymentEvent(payload()))
                .isInstanceOf(PaymentNotFoundException.class);
        }
    }
}
