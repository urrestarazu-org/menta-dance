package com.menta.billing.application.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Application-layer normalization of the two purchase-exception outbox
 * producers (D1's {@code billing.PurchaseExceptioned} and D10's
 * {@code billing.PaymentFulfillmentFailed}) into one shape the
 * notification port can consume (design Phase C consumer).
 *
 * @param paymentId the payment whose fulfillment could not be completed.
 * @param purchaseId the {@code Purchase} row that flipped to {@code EXCEPTION}, or {@code null}
 *     when this notification originates from the payment-level producer (no {@code Purchase} row exists).
 * @param userId the buyer to notify, or {@code null} when it could not be resolved (site 130,
 *     {@code payment == null}).
 * @param reason the codified {@code Reason} enum name that triggered the transition/fallback.
 * @param occurredAt instant the originating event was appended.
 * @param eventType the originating outbox event type, carried through for ops correlation (design C5).
 */
public record PurchaseExceptionNotification(
    UUID paymentId,
    UUID purchaseId,
    UUID userId,
    String reason,
    Instant occurredAt,
    String eventType
) {
}
