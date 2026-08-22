package com.menta.billing.domain.model;

/**
 * Post-payment local fulfillment state retained for {@link Purchase} and
 * {@link Subscription}.
 *
 * <p>docs/06-BILLING-API.md only specifies this state machine for {@code
 * Purchase} ("PENDING_FULFILLMENT... ASSIGNED o EXCEPTION"). {@code
 * Subscription} is mentioned alongside it with no state machine of its own.
 * For subscriptions, {@code ASSIGNED} means Billing's immutable entitlement
 * snapshot is available for Virtual to read; it does not represent a
 * write-side call into Virtual (ADR-0039).</p>
 */
public enum FulfillmentStatus {
    PENDING_FULFILLMENT,
    ASSIGNED,
    EXCEPTION
}
