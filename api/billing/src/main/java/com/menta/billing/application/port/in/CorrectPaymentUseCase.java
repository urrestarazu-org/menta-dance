package com.menta.billing.application.port.in;

import com.menta.billing.application.dto.CorrectPaymentCommand;

/**
 * Admin exceptional correction of a bank-transfer payment stuck in {@code
 * ReconciliationRequired} (#33, US-BILLING-005, design D9).
 */
public interface CorrectPaymentUseCase {

    /**
     * Applies an admin's decision to a payment that is stuck in {@code ReconciliationRequired}.
     *
     * <p>Fails with {@code PaymentNotFoundException} for an unknown payment and, before any write,
     * with {@code IllegalPaymentStateTransitionException} when the payment is in any other status.
     * On success the payment becomes completed or rejected according to the decision, fulfillment
     * is ensured when approved and released otherwise, the open reconciliation task of the payment
     * is resolved, and one {@code CORRECTION} audit row is appended.
     */
    void correct(CorrectPaymentCommand command);
}
