package com.menta.billing.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * Which subscription a {@link CancelSubscriptionCommand} cancels (US-BILLING-011, design.md A4).
 *
 * <p>Mirrors the repo's existing sealed {@code PaymentTarget}: one use case, one authorization
 * and transition path, so the self-service and admin routes cannot drift from each other.
 */
public sealed interface CancellationTarget {

    /** The acting user's own {@code ACTIVE} subscription — resolved by {@code actingUserId}. */
    record Own() implements CancellationTarget {
    }

    /** A specific subscription by id — only reachable by an admin (design.md A5). */
    record ById(UUID subscriptionId) implements CancellationTarget {
        public ById {
            Objects.requireNonNull(subscriptionId, "subscriptionId cannot be null");
        }
    }
}
