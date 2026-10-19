package com.menta.billing.application.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Notification payload for a submitted bank-transfer payment proof (#31, US-BILLING-003, design
 * D2/C12). Only the configured operations mailbox is notified — unlike {@link
 * PurchaseExceptionNotification}, there is no buyer-facing message here (design D2: notification
 * identity and authorization identity are two separate mechanisms, and a proof submission is not
 * an exception the buyer needs to hear about — they are the one who just acted).
 *
 * @param paymentId the payment the proof was submitted against.
 * @param userId the owner who submitted the proof.
 * @param originalFilename the uploaded file's original name, carried as metadata only (design C5).
 * @param occurredAt the proof's {@code uploadedAt} instant.
 */
public record PaymentProofNotification(
    UUID paymentId,
    UUID userId,
    String originalFilename,
    Instant occurredAt
) {
}
