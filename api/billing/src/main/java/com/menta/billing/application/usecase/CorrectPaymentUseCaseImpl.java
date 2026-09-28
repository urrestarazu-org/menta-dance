package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.CorrectPaymentCommand;
import com.menta.billing.application.port.in.CorrectPaymentUseCase;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PaymentAuditRepository;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.application.port.out.ReconciliationTaskRepository;
import com.menta.billing.domain.exception.PaymentNotFoundException;
import com.menta.billing.domain.model.ManualVerificationDecision;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentAuditAction;
import com.menta.billing.domain.model.PaymentId;
import lombok.RequiredArgsConstructor;

/**
 * Admin exceptional correction of a bank-transfer payment stuck in {@code
 * ReconciliationRequired} (#33, US-BILLING-005, design D9/C1/C7/C11). {@link
 * Payment#correctManually} is loud on any status other than {@code ReconciliationRequired} — that
 * exception propagates before any write, mirroring {@link ResolvePaymentProofUseCaseImpl}. On
 * success: transition, fulfillment settlement, reconciliation-task resolution (a {@code 0}-row
 * result is not an error — C11), then one audit row whose {@code reason} composes the submitted
 * evidence per D4: {@code "{motivo} | evidencia: {evidence}"}.
 */
@RequiredArgsConstructor
public class CorrectPaymentUseCaseImpl implements CorrectPaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final PaymentFulfillmentService paymentFulfillmentService;
    private final ReconciliationTaskRepository reconciliationTaskRepository;
    private final PaymentAuditRepository paymentAuditRepository;
    private final Clock clock;

    @Override
    public void correct(CorrectPaymentCommand command) {
        PaymentId id = PaymentId.of(command.paymentId());
        Payment payment = paymentRepository.findById(id).orElseThrow(() -> new PaymentNotFoundException(id));
        Payment corrected = payment.correctManually(command.decision(), clock.now());
        paymentRepository.save(corrected);
        if (command.decision() == ManualVerificationDecision.APPROVED) {
            paymentFulfillmentService.ensure(corrected);
        } else {
            paymentFulfillmentService.release(corrected);
        }
        reconciliationTaskRepository.resolveOpenByPaymentId(id, clock.now(), command.adminId());
        paymentAuditRepository.append(
            id, command.adminId(), PaymentAuditAction.CORRECTION,
            command.reason() + " | evidencia: " + command.evidence()
        );
    }
}
