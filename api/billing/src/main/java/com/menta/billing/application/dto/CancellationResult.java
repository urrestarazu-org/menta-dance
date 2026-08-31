package com.menta.billing.application.dto;

import com.menta.billing.domain.model.SubscriptionStatus;
import java.time.Instant;

/**
 * What a cancellation returns (US-BILLING-011): the subscription's new status, when access
 * actually ends, and the plan's cancellation policy text.
 *
 * <p>Deliberately carries no {@code cancellationReason} — its absence here, not a serializer
 * setting, is what guarantees D2: the reason never reaches a student-facing response.
 */
public record CancellationResult(
    String subscriptionId, SubscriptionStatus status, Instant accessEndsAt, String cancellationPolicy
) {
}
