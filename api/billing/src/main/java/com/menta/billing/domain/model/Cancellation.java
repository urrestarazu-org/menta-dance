package com.menta.billing.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Audit trail for a {@link Subscription} cancellation (US-BILLING-011): who cancelled it,
 * when, and why.
 *
 * <p>Modeled as a single nullable value object on {@link Subscription} rather than three
 * loose scalars because the three values are always written together, exactly once, and are
 * never read independently. {@code reason} is optional: it is mandatory only when the acting
 * user is not the subscription's owner, a rule enforced by {@link Subscription#cancel} rather
 * than by this record.
 */
public record Cancellation(Instant at, UUID by, String reason) {

    public Cancellation {
        Objects.requireNonNull(at, "at cannot be null");
        Objects.requireNonNull(by, "by cannot be null");
    }
}
