package com.menta.billing.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Notification payload for a buyer-facing approve/reject decision email (#33, US-BILLING-005,
 * design D5/C8). Deliberately carries no email address — {@link
 * com.menta.billing.application.port.out.PaymentDecisionNotificationPort}'s implementation
 * resolves it through {@code UserContactPort}, entirely inside infrastructure; billing's
 * application layer only ever knows a bare {@code buyerUserId}.
 *
 * @param paymentId the payment the decision was made on.
 * @param buyerUserId the payment's owner, never a client-supplied value.
 * @param targetReference the payment's target reference (subscription plan or physical course).
 * @param amount the payment's expected amount.
 * @param currency the payment's expected currency.
 * @param reason the admin's rejection motivo, verbatim; {@code null} on approval.
 */
public record PaymentDecisionNotification(
    UUID paymentId,
    UUID buyerUserId,
    String targetReference,
    BigDecimal amount,
    String currency,
    String reason
) {
}
