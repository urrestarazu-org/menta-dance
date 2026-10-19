package com.menta.shared.billing;

import java.time.Instant;
import java.util.UUID;

/**
 * Single source of truth for the {@code billing.PurchaseResolved} outbox payload: an admin closed
 * an {@code EXCEPTION} purchase without any fulfillment. The free-text note is deliberately not
 * part of the event; it stays on the purchase.
 *
 * @param paymentId the payment of the resolved purchase.
 * @param purchaseId the purchase that moved to {@code RESOLVED}.
 * @param userId the buyer, resolved from the owning payment; may be {@code null} if it is gone.
 * @param resolution the {@code PurchaseResolutionType} name: {@code REFUNDED} or {@code COMPENSATED}.
 * @param resolvedBy the admin user id that took the decision.
 * @param occurredAt instant the resolution was recorded.
 */
public record PurchaseResolvedOutboxPayload(
    UUID paymentId,
    UUID purchaseId,
    UUID userId,
    String resolution,
    UUID resolvedBy,
    Instant occurredAt
) {
}
