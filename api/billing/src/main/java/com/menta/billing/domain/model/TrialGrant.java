package com.menta.billing.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Audit trail for an admin-assigned {@link Subscription} trial (US-BILLING-012): who granted
 * it, when, why, and for how many days.
 *
 * <p>Modeled as a single value object rather than four loose scalars, same discipline as
 * {@link Cancellation}: the four values are always written together, exactly once, by
 * {@link Subscription#trial}, and are never read independently. Unlike {@code Cancellation}'s
 * {@code reason}, this one is mandatory (design D5): free access granted by a human must always
 * be explainable afterwards.
 */
public record TrialGrant(Instant at, UUID by, String reason, int days) {

    public TrialGrant {
        Objects.requireNonNull(at, "at cannot be null");
        Objects.requireNonNull(by, "by cannot be null");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason cannot be null or blank");
        }
        if (days <= 0) {
            throw new IllegalArgumentException("days must be positive");
        }
    }
}
