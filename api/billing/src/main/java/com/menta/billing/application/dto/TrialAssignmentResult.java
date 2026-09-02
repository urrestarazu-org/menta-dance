package com.menta.billing.application.dto;

import com.menta.billing.domain.model.Subscription;
import com.menta.billing.domain.model.SubscriptionStatus;
import com.menta.billing.domain.model.SubscriptionType;
import com.menta.billing.domain.model.TrialGrant;
import java.time.Instant;

/**
 * What an admin-assigned trial grant returns (US-BILLING-012, design D3).
 *
 * <p>Deliberately a new type, not a reuse of {@link SubscriptionCheckoutResult}: {@code
 * checkoutUrl}, {@code providerPreferenceId} and {@code overlapNotice} are meaningless for a
 * trial, which never opens a payment preference. Same discipline as D2 of #130 — structurally
 * impossible states over nullable-and-ignored fields.</p>
 */
public record TrialAssignmentResult(
    String subscriptionId, String userId, String planId, SubscriptionType type, SubscriptionStatus status,
    Instant startDate, Instant endDate, int days
) {
    public static TrialAssignmentResult from(Subscription subscription) {
        return new TrialAssignmentResult(
            subscription.getId().toString(),
            subscription.getUserId().toString(),
            subscription.getPlanId().toString(),
            subscription.getType(),
            subscription.getStatus(),
            subscription.getStartDate().orElse(null),
            subscription.getEndDate().orElse(null),
            subscription.getTrialGrant().map(TrialGrant::days).orElse(0)
        );
    }
}
