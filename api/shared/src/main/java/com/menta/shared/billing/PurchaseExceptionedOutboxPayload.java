package com.menta.shared.billing;

import java.time.Instant;
import java.util.UUID;

/**
 * Single source of truth for the {@code billing.PurchaseExceptioned} outbox
 * payload (proposal D1; design C3 — a dedicated event type per producer, no
 * shared nullable-{@code purchaseId} event).
 *
 * <p>Producer ({@link com.menta.billing.application.usecase.MarkPurchaseExceptionUseCase})
 * and consumer (Phase C's {@code PurchaseExceptionNotificationOutboxEventHandler})
 * import the SAME record from {@code api:shared}, so Jackson encoding on the
 * producer side and decoding on the consumer side cannot silently desync.</p>
 *
 * @param paymentId the payment whose fulfillment could not be completed.
 * @param purchaseId the {@code Purchase} row that flipped to {@code EXCEPTION}.
 * @param userId the buyer to notify, resolved from the owning {@code Payment}.
 * @param reason the codified {@link com.menta.billing.domain.model.Reason} name that triggered the transition.
 * @param occurredAt instant the transition was persisted.
 */
public record PurchaseExceptionedOutboxPayload(
    UUID paymentId,
    UUID purchaseId,
    UUID userId,
    String reason,
    Instant occurredAt
) {
}
