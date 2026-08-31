package com.menta.billing.infrastructure.web.dto;

import com.menta.billing.application.dto.CancellationResult;
import java.time.Instant;

/**
 * {@code 200 OK} body for a subscription cancellation (US-BILLING-011), shared by both the
 * self-service and admin routes.
 *
 * <p>Deliberately has no {@code cancellationReason} component. That absence is structural
 * (design.md D2): a student reading either their own subscription or this response can never
 * see why an admin cancelled it, because the field does not exist here — not because it was
 * filtered out or serialized as {@code null}.</p>
 */
public record CancelSubscriptionResponse(
    String subscriptionId, String status, Instant accessEndsAt, String cancellationPolicy
) {
    public static CancelSubscriptionResponse from(CancellationResult result) {
        return new CancelSubscriptionResponse(
            result.subscriptionId(), result.status().name(), result.accessEndsAt(), result.cancellationPolicy()
        );
    }
}
