package com.menta.billing.application.usecase;

import com.menta.billing.domain.model.Payment;

/** What {@link PaymentVerificationService#verify} did — tells the worker how to close out the inbox row. */
public sealed interface VerificationOutcome {

    /** Verified (or already-terminal) — mark the inbox row PROCESSED regardless of the resulting status. */
    record Applied(Payment payment) implements VerificationOutcome {
    }

    /** No local {@code Payment} for this {@code providerPaymentId} — create a reconciliation task, not a Payment. */
    record NoLocalPayment() implements VerificationOutcome {
    }
}
