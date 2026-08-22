package com.menta.billing.application.dto;

import com.menta.billing.domain.model.PaymentMethod;
import java.util.Objects;
import java.util.UUID;

/**
 * Input for {@code POST /api/v1/billing/subscriptions} (US-BILLING-010).
 *
 * <p>{@code userId} comes from the access token, never from the request body:
 * a client can never subscribe someone else.</p>
 *
 * @param idempotencyKey client-supplied; replaying it returns the same
 *     subscription and payment instead of opening a second charge
 *     (escenario 5)
 */
public record CreateSubscriptionCheckoutCommand(
    UUID userId, String planId, PaymentMethod paymentMethod, String idempotencyKey
) {
    public CreateSubscriptionCheckoutCommand {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(paymentMethod, "paymentMethod cannot be null");
        if (planId == null || planId.isBlank()) {
            throw new IllegalArgumentException("planId cannot be null or blank");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey cannot be null or blank");
        }
    }
}
