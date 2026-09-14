package com.menta.billing.application.dto;

import com.menta.billing.domain.model.PaymentMethod;
import java.util.Objects;
import java.util.UUID;

/**
 * Input for {@code POST /api/v1/billing/physical/purchases} (#41,
 * US-PHYSICAL-004).
 *
 * <p>{@code userId} comes from the access token, never from the request
 * body — same discipline as {@code CreateSubscriptionCheckoutCommand}: a
 * client can never buy a physical course on someone else's behalf.</p>
 *
 * @param idempotencyKey client-supplied; replaying it with the same user
 *     returns the same checkout data instead of opening a second
 *     {@code Payment}
 */
public record CreatePhysicalPurchaseCheckoutCommand(
    UUID userId, String quoteId, PaymentMethod paymentMethod, String idempotencyKey
) {
    public CreatePhysicalPurchaseCheckoutCommand {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(paymentMethod, "paymentMethod cannot be null");
        if (quoteId == null || quoteId.isBlank()) {
            throw new IllegalArgumentException("quoteId cannot be null or blank");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey cannot be null or blank");
        }
    }
}
