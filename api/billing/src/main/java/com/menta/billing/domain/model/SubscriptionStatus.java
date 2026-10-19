package com.menta.billing.domain.model;

/**
 * Lifecycle of a {@link Subscription} — deliberately its own concept, NOT a
 * reuse of {@link FulfillmentStatus}.
 *
 * <p>{@code FulfillmentStatus} is shared with {@link Purchase} and answers a
 * different question ("did the access grant / capacity assignment succeed?").
 * A subscription needs both axes at once: US-BILLING-010's NFR says the
 * financial settlement never waits on fulfillment, so {@code ACTIVE} with a
 * failed grant ({@code EXCEPTION}) is a legitimate, representable state.
 * Folding the two into one enum would make it unrepresentable and would
 * force {@code Purchase} to carry subscription-only states it can never
 * reach.</p>
 *
 * <p>{@code CANCELLED} and {@code EXPIRED} are separate on purpose
 * (US-BILLING-011): cancellation is a decision, expiry is the passage of
 * time, and a report that cannot tell them apart is useless. Neither the
 * cancellation endpoint nor the automatic expiry sweep is built here — this
 * enum only makes them representable so those stories can be layered on
 * without another migration.</p>
 */
public enum SubscriptionStatus {

    /** Checkout started, provider has not confirmed the payment yet. */
    PENDING,

    /** Paid and in force between {@code startDate} and {@code endDate}. */
    ACTIVE,

    /** Its {@code endDate} passed (US-BILLING-004). */
    EXPIRED,

    /**
     * Ended by a decision rather than by time — a user cancellation
     * (US-BILLING-011) or a checkout whose payment never settled
     * (US-BILLING-010 escenario 6). Both release the user's subscription
     * slot, which is why they share a state.
     */
    CANCELLED;

    /**
     * Whether this state occupies the user's single subscription slot. Only
     * these two block a new checkout — {@code EXPIRED} and {@code CANCELLED}
     * free it (escenario 6: "el alumno puede iniciar una suscripción nueva").
     */
    public boolean occupiesUserSlot() {
        return this == PENDING || this == ACTIVE;
    }
}
