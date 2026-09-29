package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.PaymentDecisionNotification;
import com.menta.billing.application.dto.ResolvePaymentProofCommand;
import com.menta.billing.application.port.in.ResolvePaymentProofUseCase;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PaymentAuditRepository;
import com.menta.billing.application.port.out.PaymentDecisionNotificationPort;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.domain.exception.PaymentNotFoundException;
import com.menta.billing.domain.model.ManualVerificationDecision;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentAuditAction;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentTarget;

/**
 * Admin approve/reject of a bank-transfer payment proof (#31, US-BILLING-003, design D1). {@link
 * Payment#resolveManually} is loud on any status other than {@code AwaitingManualVerification}
 * (C4) — that exception propagates before any write, and {@link PaymentFulfillmentService} is
 * never called. Gains two added outbound calls for #33, US-BILLING-005: an audit-trail append per
 * branch (design D4/C7), then a buyer decision email (design D5/D8/C8/C9). Transition and
 * fulfillment logic are otherwise byte-unchanged.
 */
public class ResolvePaymentProofUseCaseImpl implements ResolvePaymentProofUseCase {

    private final PaymentRepository paymentRepository;
    private final PaymentFulfillmentService paymentFulfillmentService;
    private final PaymentAuditRepository paymentAuditRepository;
    private final PaymentDecisionNotificationPort paymentDecisionNotificationPort;
    private final Clock clock;

    public ResolvePaymentProofUseCaseImpl(
        PaymentRepository paymentRepository, PaymentFulfillmentService paymentFulfillmentService,
        PaymentAuditRepository paymentAuditRepository,
        PaymentDecisionNotificationPort paymentDecisionNotificationPort, Clock clock
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentFulfillmentService = paymentFulfillmentService;
        this.paymentAuditRepository = paymentAuditRepository;
        this.paymentDecisionNotificationPort = paymentDecisionNotificationPort;
        this.clock = clock;
    }

    @Override
    public void resolve(ResolvePaymentProofCommand command) {
        PaymentId id = PaymentId.of(command.paymentId());
        Payment payment = paymentRepository.findById(id).orElseThrow(() -> new PaymentNotFoundException(id));
        Payment resolved = payment.resolveManually(command.decision(), clock.now());
        paymentRepository.save(resolved);
        if (command.decision() == ManualVerificationDecision.APPROVED) {
            paymentFulfillmentService.ensure(resolved);
            paymentAuditRepository.append(id, command.adminId(), PaymentAuditAction.APPROVE, null);
            paymentDecisionNotificationPort.notifyApproved(notificationFor(resolved, null));
        } else {
            paymentFulfillmentService.release(resolved);
            paymentAuditRepository.append(id, command.adminId(), PaymentAuditAction.REJECT, command.reason());
            paymentDecisionNotificationPort.notifyRejected(notificationFor(resolved, command.reason()));
        }
    }

    private static PaymentDecisionNotification notificationFor(Payment payment, String reason) {
        return new PaymentDecisionNotification(
            payment.getId().getValue(), payment.getUserId(), targetReference(payment.getTarget()),
            payment.getExpectedAmount().getAmount(), payment.getExpectedAmount().getCurrency(), reason
        );
    }

    private static String targetReference(PaymentTarget target) {
        return switch (target) {
            case PaymentTarget.Physical physical -> physical.quoteId();
            case PaymentTarget.Virtual virtual -> virtual.planId();
        };
    }
}
