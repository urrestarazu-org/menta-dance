package com.menta.billing.domain.model;

/**
 * Post-payment fulfillment state, shared by {@link Purchase} (physical
 * capacity) and {@link Subscription} (virtual access).
 *
 * <p>docs/06-BILLING-API.md only specifies this state machine for {@code
 * Purchase} ("PENDING_FULFILLMENT... ASSIGNED o EXCEPTION"). {@code
 * Subscription} is mentioned alongside it with no state machine of its own
 * — reusing the same three states here is this issue's interpretation, not
 * something the doc states literally: both depend on the identical
 * shape (a placeholder port that may throw), so one enum covers both rather
 * than inventing a second, narrower one.</p>
 */
public enum FulfillmentStatus {
    PENDING_FULFILLMENT,
    ASSIGNED,
    EXCEPTION
}
