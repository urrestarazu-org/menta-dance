package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.PaymentDecisionNotification;
import com.menta.billing.application.dto.ResolvePaymentProofCommand;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PaymentAuditRepository;
import com.menta.billing.application.port.out.PaymentDecisionNotificationPort;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.domain.exception.IllegalPaymentStateTransitionException;
import com.menta.billing.domain.model.ManualVerificationDecision;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentAuditAction;
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
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final PaymentId PAYMENT_ID = PaymentId.generate();
    private static final Money AMOUNT = Money.of(BigDecimal.TEN, "ARS");

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentFulfillmentService fulfillmentService = mock(PaymentFulfillmentService.class);
    private final PaymentAuditRepository auditRepository = mock(PaymentAuditRepository.class);
    private final PaymentDecisionNotificationPort notificationPort = mock(PaymentDecisionNotificationPort.class);

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

    private ResolvePaymentProofUseCaseImpl useCase() {
        return new ResolvePaymentProofUseCaseImpl(
            paymentRepository, fulfillmentService, auditRepository, notificationPort, fixedClock()
        );
    }

    @Test
    void approving_completes_the_payment_and_ensures_fulfillment() {
        when(paymentRepository.findById(PAYMENT_ID))
            .thenReturn(Optional.of(paymentWith(new PaymentStatus.AwaitingManualVerification())));
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        useCase().resolve(new ResolvePaymentProofCommand(
            PAYMENT_ID.toString(), ManualVerificationDecision.APPROVED, null, ADMIN_ID
        ));

        InOrder order = inOrder(paymentRepository, fulfillmentService);
        order.verify(paymentRepository).save(argThatCompleted());
        order.verify(fulfillmentService).ensure(argThatCompleted());
        verify(fulfillmentService, never()).release(any());
    }

    @Test
    void rejecting_rejects_the_payment_and_releases_fulfillment() {
        when(paymentRepository.findById(PAYMENT_ID))
            .thenReturn(Optional.of(paymentWith(new PaymentStatus.AwaitingManualVerification())));
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        useCase().resolve(new ResolvePaymentProofCommand(
            PAYMENT_ID.toString(), ManualVerificationDecision.REJECTED, "Comprobante ilegible", ADMIN_ID
        ));

        InOrder order = inOrder(paymentRepository, fulfillmentService);
        order.verify(paymentRepository).save(argThatRejected());
        order.verify(fulfillmentService).release(argThatRejected());
        verify(fulfillmentService, never()).ensure(any());
    }

    /** C4: a second decision on an already-resolved payment must never write or settle anything. */
    @Test
    void an_already_resolved_payment_throws_and_writes_nothing() {
        when(paymentRepository.findById(PAYMENT_ID))
            .thenReturn(Optional.of(paymentWith(new PaymentStatus.Expired(CREATED_AT))));

        assertThatThrownBy(() -> useCase().resolve(new ResolvePaymentProofCommand(
            PAYMENT_ID.toString(), ManualVerificationDecision.APPROVED, null, ADMIN_ID
        ))).isInstanceOf(IllegalPaymentStateTransitionException.class);

        verify(paymentRepository, never()).save(any());
        verify(fulfillmentService, never()).ensure(any());
        verify(fulfillmentService, never()).release(any());
        verifyNoInteractions(auditRepository);
        verifyNoInteractions(notificationPort);
    }

    /** #33, US-BILLING-005, D4/C7: approve writes exactly one audit row, no reason. */
    @Test
    void approving_writes_one_audit_row_with_no_reason() {
        when(paymentRepository.findById(PAYMENT_ID))
            .thenReturn(Optional.of(paymentWith(new PaymentStatus.AwaitingManualVerification())));
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        useCase().resolve(new ResolvePaymentProofCommand(
            PAYMENT_ID.toString(), ManualVerificationDecision.APPROVED, null, ADMIN_ID
        ));

        verify(auditRepository).append(PAYMENT_ID, ADMIN_ID, PaymentAuditAction.APPROVE, null);
    }

    /** #33, US-BILLING-005, D4/C7: reject writes exactly one audit row carrying the reason. */
    @Test
    void rejecting_writes_one_audit_row_with_the_reason() {
        when(paymentRepository.findById(PAYMENT_ID))
            .thenReturn(Optional.of(paymentWith(new PaymentStatus.AwaitingManualVerification())));
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        useCase().resolve(new ResolvePaymentProofCommand(
            PAYMENT_ID.toString(), ManualVerificationDecision.REJECTED, "Comprobante ilegible", ADMIN_ID
        ));

        verify(auditRepository)
            .append(PAYMENT_ID, ADMIN_ID, PaymentAuditAction.REJECT, "Comprobante ilegible");
    }

    /**
     * #33, US-BILLING-005, D5/D8/R6: approve calls {@code notifyApproved} exactly once and never
     * the unrelated ops-mailbox adapter (design D2's separation — this test asserts against
     * {@code notificationPort} being the only outbound notification collaborator, matching the
     * Success Criteria's "the ops-mailbox adapter is not invoked").
     */
    @Test
    void approving_sends_a_confirmation_email_and_never_the_rejection_template() {
        when(paymentRepository.findById(PAYMENT_ID))
            .thenReturn(Optional.of(paymentWith(new PaymentStatus.AwaitingManualVerification())));
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        useCase().resolve(new ResolvePaymentProofCommand(
            PAYMENT_ID.toString(), ManualVerificationDecision.APPROVED, null, ADMIN_ID
        ));

        verify(notificationPort, times(1)).notifyApproved(any(PaymentDecisionNotification.class));
        verify(notificationPort, never()).notifyRejected(any());
    }

    /** #33, US-BILLING-005, D5/D8/R6: reject calls {@code notifyRejected} carrying the reason. */
    @Test
    void rejecting_sends_a_rejection_email_with_the_reason() {
        when(paymentRepository.findById(PAYMENT_ID))
            .thenReturn(Optional.of(paymentWith(new PaymentStatus.AwaitingManualVerification())));
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        useCase().resolve(new ResolvePaymentProofCommand(
            PAYMENT_ID.toString(), ManualVerificationDecision.REJECTED, "Comprobante ilegible", ADMIN_ID
        ));

        verify(notificationPort, times(1)).notifyRejected(org.mockito.ArgumentMatchers.argThat(
            notification -> "Comprobante ilegible".equals(notification.reason())
                && PAYMENT_ID.getValue().equals(notification.paymentId())
                && USER_ID.equals(notification.buyerUserId())
        ));
        verify(notificationPort, never()).notifyApproved(any());
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
