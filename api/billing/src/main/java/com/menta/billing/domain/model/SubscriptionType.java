package com.menta.billing.domain.model;

/**
 * Whether a {@link Subscription} was paid for or granted free of charge (US-BILLING-012).
 *
 * <p>Descriptive only (design D6): never used as an authorization input in the access
 * decision, the automatic expiry sweep, or the user-slot check — a {@code TRIAL} subscription
 * behaves identically to a {@code PAID} one everywhere except how it was created.</p>
 */
public enum SubscriptionType {
    PAID,
    TRIAL
}
