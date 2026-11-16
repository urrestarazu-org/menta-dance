package com.menta.billing.application.dto;

import com.menta.billing.domain.model.SubscriptionType;
import java.time.Instant;

/**
 * The current-subscription resolution's outcome (US-BILLING-004, design.md A4). Sealed so the
 * compiler enforces exhaustive handling exactly where the 100% domain+application coverage floor
 * bites — mirrors this module's existing {@link CancellationTarget} / {@code PaymentTarget} sum
 * types.
 *
 * <p>{@code EXPIRING_SOON} is not a separate variant: it is folded into {@link Active} via its
 * {@code expiringSoon} flag, matching how {@code Subscription.isExpiringSoon} is defined only for
 * {@code ACTIVE} subscriptions.
 *
 * <p>Every variant carries the subscription's {@link SubscriptionType} (#766), so a client can tell
 * an admin-granted trial from a paid subscription; it is descriptive only and never changes which
 * variant resolves.
 */
public sealed interface CurrentSubscriptionResult {

    /** Status {@code ACTIVE}: in force between {@code startDate} and {@code endDate}. */
    record Active(
        String subscriptionId, String planId, Instant startDate, Instant endDate, long daysRemaining,
        boolean expiringSoon, SubscriptionType type
    ) implements CurrentSubscriptionResult {
    }

    /** Status {@code EXPIRED}: {@code endDate} already passed (D1's third resolvable state). */
    record Expired(
        String subscriptionId, String planId, Instant startDate, Instant endDate, SubscriptionType type
    ) implements CurrentSubscriptionResult {
    }

    /** Status {@code PENDING}: no dates yet, only the existing checkout URL to complete payment. */
    record PendingPayment(String subscriptionId, String planId, String checkoutUrl, SubscriptionType type)
        implements CurrentSubscriptionResult {
    }
}
