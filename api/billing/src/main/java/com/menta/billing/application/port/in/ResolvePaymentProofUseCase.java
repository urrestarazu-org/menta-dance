package com.menta.billing.application.port.in;

import com.menta.billing.application.dto.ResolvePaymentProofCommand;

/** Admin approve/reject of a bank-transfer payment proof (#31, US-BILLING-003, design D1). */
public interface ResolvePaymentProofUseCase {

    /**
     * Approves or rejects a bank-transfer payment that awaits manual verification.
     *
     * <p>Fails with {@code PaymentNotFoundException} for an unknown payment and, before any write,
     * with {@code IllegalPaymentStateTransitionException} when the payment is not in {@code
     * AwaitingManualVerification}. Approval ensures fulfillment and rejection releases it; either
     * way one audit row is appended and the buyer is notified of the decision.
     */
    void resolve(ResolvePaymentProofCommand command);
}
