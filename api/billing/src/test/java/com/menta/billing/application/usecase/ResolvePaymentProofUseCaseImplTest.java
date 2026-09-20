package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.ResolvePaymentProofCommand;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.domain.exception.IllegalPaymentStateTransitionException;
import com.menta.billing.domain.model.ManualVerificationDecision;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentStatus;
import com.menta.billing.domain.model.PaymentTarget;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * Admin resolution of a bank-transfer payment (#31, US-BILLING-003, design D1/C4). Approve
 * settles through {@link PaymentFulfillmentService#ensure(Payment)}, reject through {@link
 * PaymentFulfillmentService#release(Payment)} — the same collaborator the MP path and the P5
 * sweep already use.
 */
class ResolvePaymentProofUseCaseImplTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-19T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final PaymentId PAYMENT_ID = PaymentId.generate();
    private static final Money AMOUNT = Money.of(BigDecimal.TEN, "ARS");

    private static Payment paymentWith(PaymentStatus status) {
        return new Payment(
            PAYMENT_ID, USER_ID, null, AMOUNT, "ext-1", "merchant-1",
            new PaymentTarget.Virtual("plan-1"), status, CREATED_AT
        );
    }

    private static Clock fixedClock() {
        Clock clock = mock(Clock.class);
        when(clock.now()).thenReturn(NOW);
        return clock;
    }

    @Test
    void approving_completes_the_payment_and_ensures_fulfillment() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentFulfillmentService fulfillmentService = mock(PaymentFulfillmentService.class);
        when(paymentRepository.findById(PAYMENT_ID))
            .thenReturn(Optional.of(paymentWith(new PaymentStatus.AwaitingManualVerification())));
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ResolvePaymentProofUseCaseImpl useCase =
            new ResolvePaymentProofUseCaseImpl(paymentRepository, fulfillmentService, fixedClock());

        useCase.resolve(new ResolvePaymentProofCommand(
            PAYMENT_ID.toString(), ManualVerificationDecision.APPROVED, null
        ));

        InOrder order = inOrder(paymentRepository, fulfillmentService);
        order.verify(paymentRepository).save(argThatCompleted());
        order.verify(fulfillmentService).ensure(argThatCompleted());
        verify(fulfillmentService, never()).release(any());
    }

    @Test
    void rejecting_rejects_the_payment_and_releases_fulfillment() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentFulfillmentService fulfillmentService = mock(PaymentFulfillmentService.class);
        when(paymentRepository.findById(PAYMENT_ID))
            .thenReturn(Optional.of(paymentWith(new PaymentStatus.AwaitingManualVerification())));
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ResolvePaymentProofUseCaseImpl useCase =
            new ResolvePaymentProofUseCaseImpl(paymentRepository, fulfillmentService, fixedClock());

        useCase.resolve(new ResolvePaymentProofCommand(
            PAYMENT_ID.toString(), ManualVerificationDecision.REJECTED, "Comprobante ilegible"
        ));

        InOrder order = inOrder(paymentRepository, fulfillmentService);
        order.verify(paymentRepository).save(argThatRejected());
        order.verify(fulfillmentService).release(argThatRejected());
        verify(fulfillmentService, never()).ensure(any());
    }

    /** C4: a second decision on an already-resolved payment must never write or settle anything. */
    @Test
    void an_already_resolved_payment_throws_and_writes_nothing() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentFulfillmentService fulfillmentService = mock(PaymentFulfillmentService.class);
        when(paymentRepository.findById(PAYMENT_ID))
            .thenReturn(Optional.of(paymentWith(new PaymentStatus.Expired(CREATED_AT))));
        ResolvePaymentProofUseCaseImpl useCase =
            new ResolvePaymentProofUseCaseImpl(paymentRepository, fulfillmentService, fixedClock());

        assertThatThrownBy(() -> useCase.resolve(new ResolvePaymentProofCommand(
            PAYMENT_ID.toString(), ManualVerificationDecision.APPROVED, null
        ))).isInstanceOf(IllegalPaymentStateTransitionException.class);

        verify(paymentRepository, never()).save(any());
        verify(fulfillmentService, never()).ensure(any());
        verify(fulfillmentService, never()).release(any());
    }

    private static Payment argThatCompleted() {
        return org.mockito.ArgumentMatchers.argThat(
            payment -> payment.getStatus() instanceof PaymentStatus.Completed
        );
    }

    private static Payment argThatRejected() {
        return org.mockito.ArgumentMatchers.argThat(
            payment -> payment.getStatus() instanceof PaymentStatus.Rejected
        );
    }
}
