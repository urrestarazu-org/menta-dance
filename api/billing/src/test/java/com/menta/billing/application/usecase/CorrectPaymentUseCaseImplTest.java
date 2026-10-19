package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.CorrectPaymentCommand;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PaymentAuditRepository;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.application.port.out.ReconciliationTaskRepository;
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
 * #33, US-BILLING-005, design D9/C1/C7/C11: the corrections flow — transition, fulfillment, task
 * resolve, audit — in that order, and never touching a payment that is not {@code
 * ReconciliationRequired}.
 */
class CorrectPaymentUseCaseImplTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-19T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
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
    void correcting_to_approved_completes_the_payment_ensures_fulfillment_resolves_the_task_and_audits() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentFulfillmentService fulfillmentService = mock(PaymentFulfillmentService.class);
        ReconciliationTaskRepository reconciliationTaskRepository = mock(ReconciliationTaskRepository.class);
        PaymentAuditRepository auditRepository = mock(PaymentAuditRepository.class);
        when(paymentRepository.findById(PAYMENT_ID))
            .thenReturn(Optional.of(paymentWith(new PaymentStatus.ReconciliationRequired("mismatch"))));
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(reconciliationTaskRepository.resolveOpenByPaymentId(PAYMENT_ID, NOW, ADMIN_ID)).thenReturn(1);
        CorrectPaymentUseCaseImpl useCase = new CorrectPaymentUseCaseImpl(
            paymentRepository, fulfillmentService, reconciliationTaskRepository, auditRepository, fixedClock()
        );

        useCase.correct(new CorrectPaymentCommand(
            PAYMENT_ID.toString(), ManualVerificationDecision.APPROVED,
            "Transferencia confirmada por el banco", "captura-extracto.pdf", ADMIN_ID
        ));

        InOrder order = inOrder(paymentRepository, fulfillmentService, reconciliationTaskRepository, auditRepository);
        order.verify(paymentRepository).save(argThatCompleted());
        order.verify(fulfillmentService).ensure(argThatCompleted());
        order.verify(reconciliationTaskRepository).resolveOpenByPaymentId(PAYMENT_ID, NOW, ADMIN_ID);
        order.verify(auditRepository).append(
            PAYMENT_ID, ADMIN_ID, PaymentAuditAction.CORRECTION,
            "Transferencia confirmada por el banco | evidencia: captura-extracto.pdf"
        );
        verify(fulfillmentService, never()).release(any());
    }

    @Test
    void correcting_to_rejected_rejects_the_payment_and_releases_fulfillment() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentFulfillmentService fulfillmentService = mock(PaymentFulfillmentService.class);
        ReconciliationTaskRepository reconciliationTaskRepository = mock(ReconciliationTaskRepository.class);
        PaymentAuditRepository auditRepository = mock(PaymentAuditRepository.class);
        when(paymentRepository.findById(PAYMENT_ID))
            .thenReturn(Optional.of(paymentWith(new PaymentStatus.ReconciliationRequired("mismatch"))));
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(reconciliationTaskRepository.resolveOpenByPaymentId(PAYMENT_ID, NOW, ADMIN_ID)).thenReturn(1);
        CorrectPaymentUseCaseImpl useCase = new CorrectPaymentUseCaseImpl(
            paymentRepository, fulfillmentService, reconciliationTaskRepository, auditRepository, fixedClock()
        );

        useCase.correct(new CorrectPaymentCommand(
            PAYMENT_ID.toString(), ManualVerificationDecision.REJECTED,
            "No se encontró la transferencia", "captura-extracto.pdf", ADMIN_ID
        ));

        InOrder order = inOrder(paymentRepository, fulfillmentService, reconciliationTaskRepository, auditRepository);
        order.verify(paymentRepository).save(argThatRejected());
        order.verify(fulfillmentService).release(argThatRejected());
        order.verify(reconciliationTaskRepository).resolveOpenByPaymentId(PAYMENT_ID, NOW, ADMIN_ID);
        order.verify(auditRepository).append(
            PAYMENT_ID, ADMIN_ID, PaymentAuditAction.CORRECTION,
            "No se encontró la transferencia | evidencia: captura-extracto.pdf"
        );
        verify(fulfillmentService, never()).ensure(any());
    }

    /** C11: a payment with no tied reconciliation task (0 rows resolved) still succeeds. */
    @Test
    void a_zero_row_task_resolution_still_succeeds() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentFulfillmentService fulfillmentService = mock(PaymentFulfillmentService.class);
        ReconciliationTaskRepository reconciliationTaskRepository = mock(ReconciliationTaskRepository.class);
        PaymentAuditRepository auditRepository = mock(PaymentAuditRepository.class);
        when(paymentRepository.findById(PAYMENT_ID))
            .thenReturn(Optional.of(paymentWith(new PaymentStatus.ReconciliationRequired("mismatch"))));
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(reconciliationTaskRepository.resolveOpenByPaymentId(PAYMENT_ID, NOW, ADMIN_ID)).thenReturn(0);
        CorrectPaymentUseCaseImpl useCase = new CorrectPaymentUseCaseImpl(
            paymentRepository, fulfillmentService, reconciliationTaskRepository, auditRepository, fixedClock()
        );

        useCase.correct(new CorrectPaymentCommand(
            PAYMENT_ID.toString(), ManualVerificationDecision.APPROVED,
            "Transferencia confirmada por el banco", "captura-extracto.pdf", ADMIN_ID
        ));

        verify(auditRepository).append(
            PAYMENT_ID, ADMIN_ID, PaymentAuditAction.CORRECTION,
            "Transferencia confirmada por el banco | evidencia: captura-extracto.pdf"
        );
    }

    /** Scenario "Correction on a payment not requiring reconciliation is rejected" — 409, nothing changes. */
    @Test
    void a_payment_not_requiring_reconciliation_throws_and_changes_nothing() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentFulfillmentService fulfillmentService = mock(PaymentFulfillmentService.class);
        ReconciliationTaskRepository reconciliationTaskRepository = mock(ReconciliationTaskRepository.class);
        PaymentAuditRepository auditRepository = mock(PaymentAuditRepository.class);
        when(paymentRepository.findById(PAYMENT_ID))
            .thenReturn(Optional.of(paymentWith(new PaymentStatus.AwaitingManualVerification())));
        CorrectPaymentUseCaseImpl useCase = new CorrectPaymentUseCaseImpl(
            paymentRepository, fulfillmentService, reconciliationTaskRepository, auditRepository, fixedClock()
        );

        assertThatThrownBy(() -> useCase.correct(new CorrectPaymentCommand(
            PAYMENT_ID.toString(), ManualVerificationDecision.APPROVED,
            "Transferencia confirmada por el banco", "captura-extracto.pdf", ADMIN_ID
        ))).isInstanceOf(IllegalPaymentStateTransitionException.class);

        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(fulfillmentService);
        verifyNoInteractions(reconciliationTaskRepository);
        verifyNoInteractions(auditRepository);
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
