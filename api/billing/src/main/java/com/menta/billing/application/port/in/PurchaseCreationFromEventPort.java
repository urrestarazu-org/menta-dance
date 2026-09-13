package com.menta.billing.application.port.in;

import com.menta.shared.billing.PaymentCompletedOutboxPayload;
import java.util.List;

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
 *
 * <p>{@code eligibleSessionIds} is the caller-resolved coverage set (design
 * A4/A5): one session for {@code INDIVIDUAL}, N for {@code MONTHLY}. This
 * port no longer derives it from {@code payload.targetReference()} — that
 * field is now the {@code quoteId}, which cannot represent a multi-session
 * purchase by itself. The caller (Billing's {@code CoveragePlanner}, wired
 * from {@code api:app}) resolves the concrete list before calling here.</p>
 */
public interface PurchaseCreationFromEventPort {

    /**
     * Upserts the {@code Purchase} row for this paymentId, covering every
     * session in {@code eligibleSessionIds}; returns the resulting
     * {@link com.menta.billing.domain.model.Purchase} (whether newly
     * created or pre-existing). Idempotent re-delivery yields the
     * ORIGINAL session set, never a new one derived from a later call.
     */
    com.menta.billing.domain.model.Purchase createPurchaseFromPaymentEvent(
        PaymentCompletedOutboxPayload payload,
        List<String> eligibleSessionIds
    );
}
