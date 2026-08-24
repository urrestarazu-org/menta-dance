package com.menta.billing.application.port.in;

import com.menta.shared.billing.PaymentCompletedOutboxPayload;

/**
 * IN port that {@code api:app}'s outbox handler uses to upsert one
 * {@code billing_purchases} row per {@code billing.PhysicalPaymentCompleted}
 * delivery (proposal §4; design §5.4).
 *
 * <p>Idempotent keyed on {@code payload.paymentId()}: re-delivery returns
 * the existing row and never inserts a second copy. V8 line 31
 * {@code uq_billing_purchases_payment_id} is the DB-level backstop — when
 * two handlers race, the loser catches
 * {@code DataIntegrityViolationException} and re-fetches.</p>
 */
public interface PurchaseCreationFromEventPort {

    /**
     * Upserts the {@code Purchase} row for this paymentId; returns the
     * resulting {@link com.menta.billing.domain.model.Purchase} (whether
     * newly created or pre-existing).
     */
    com.menta.billing.domain.model.Purchase createPurchaseFromPaymentEvent(
        PaymentCompletedOutboxPayload payload
    );
}
