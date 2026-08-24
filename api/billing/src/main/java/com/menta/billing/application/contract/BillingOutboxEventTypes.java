package com.menta.billing.application.contract;

/**
 * Canonical event type constants for the billing module's outbox events
 * (companion to {@code com.menta.auth.application.contract.AuthOutboxEventTypes}).
 *
 * <h2>Why constants instead of an enum?</h2>
 * <p>
 * Mirrors the same rationale as {@code AuthOutboxEventTypes}: event types are
 * stored as strings in the database ({@code event_type} column) and matched
 * by consumers by exact value. Constant-holder classes are referenced
 * directly in tests and consumer code.
 * </p>
 *
 * <h2>Naming convention</h2>
 * <p>
 * {@code <module>.<EventName>}; consumers (api:app's outbox handlers)
 * match on these exact strings. Changing them requires a migration strategy.
 * </p>
 */
public final class BillingOutboxEventTypes {

    /**
     * Emitted after a {@code PaymentTarget.Physical} payment commits to
     * {@code COMPLETED}. The outbox row carries
     * {@code com.menta.shared.billing.PaymentCompletedOutboxPayload} as
     * JSON so producer and consumer fields NEVER diverge.
     */
    public static final String PHYSICAL_PAYMENT_COMPLETED = "billing.PhysicalPaymentCompleted";

    private BillingOutboxEventTypes() {
        // Constant holder.
    }
}
