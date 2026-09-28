package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.ResolvePaymentProofCommand;
import com.menta.billing.application.port.in.ResolvePaymentProofUseCase;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PaymentAuditRepository;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.domain.exception.PaymentNotFoundException;
import com.menta.billing.domain.model.ManualVerificationDecision;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentAuditAction;
import com.menta.billing.domain.model.PaymentId;

/**
 * Admin approve/reject of a bank-transfer payment proof (#31, US-BILLING-003, design D1). {@link
 * Payment#resolveManually} is loud on any status other than {@code AwaitingManualVerification}
 * (C4) — that exception propagates before any write, and {@link PaymentFulfillmentService} is
 * never called. Gains exactly one added outbound call for #33, US-BILLING-005 (design D4/C7): an
 * audit-trail append per branch. Transition and fulfillment logic are otherwise byte-unchanged.
 */
public class ResolvePaymentProofUseCaseImpl implements ResolvePaymentProofUseCase {

    private final PaymentRepository paymentRepository;
    private final PaymentFulfillmentService paymentFulfillmentService;
    private final PaymentAuditRepository paymentAuditRepository;
    private final Clock clock;

    public ResolvePaymentProofUseCaseImpl(
        PaymentRepository paymentRepository, PaymentFulfillmentService paymentFulfillmentService,
        PaymentAuditRepository paymentAuditRepository, Clock clock
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentFulfillmentService = paymentFulfillmentService;
        this.paymentAuditRepository = paymentAuditRepository;
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
        } else {
            paymentFulfillmentService.release(resolved);
            paymentAuditRepository.append(id, command.adminId(), PaymentAuditAction.REJECT, command.reason());
        }
    }
}
