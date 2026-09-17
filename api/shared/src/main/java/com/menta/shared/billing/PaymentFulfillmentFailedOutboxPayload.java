package com.menta.shared.billing;

import java.time.Instant;
import java.util.UUID;

/**
 * Single source of truth for the {@code billing.PaymentFulfillmentFailed}
 * outbox payload (proposal D10; design C1/C2 — the payment-level fallback
 * for the three pre-{@code Purchase} sites in
 * {@code PhysicalCapacityAssignmentOutboxEventHandler}).
 *
 * <p>Unlike {@link PurchaseExceptionedOutboxPayload}, there is no {@code
 * purchaseId} field: at the sites this event covers, no {@code Purchase}
 * row exists yet (design C1). Producer ({@code
 * com.menta.billing.application.usecase.PublishPaymentFulfillmentFailedUseCase})
 * and consumer (Phase C's {@code PurchaseExceptionNotificationOutboxEventHandler})
 * import the SAME record from {@code api:shared}.</p>
 *
 * @param paymentId the payment whose fulfillment could not be completed.
 * @param userId the buyer to notify, {@code null} when the {@code Payment}
 *     row itself is absent (site 130, {@code payment == null}).
 * @param reason the codified {@link com.menta.billing.domain.model.Reason} name that triggered the fallback.
 * @param occurredAt instant the event was appended.
 */
public record PaymentFulfillmentFailedOutboxPayload(
    UUID paymentId,
    UUID userId,
    String reason,
    Instant occurredAt
) {
}
