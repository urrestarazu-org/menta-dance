package com.menta.bff.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row of the manual-verification inbox ({@code PendingVerificationItemResponse} in
 * {@code billing-v1.yaml}).
 *
 * @param paymentId       payment identifier
 * @param userId          identifier of the buyer
 * @param targetModality  whether the payment buys a physical course or a virtual plan
 * @param targetReference reference of what is bought: a plan or a physical course
 * @param amount          amount to verify
 * @param currency        ISO 4217 currency code of the amount
 * @param statusType      where the payment stands; always awaiting manual verification in this inbox
 * @param hasProof        whether the buyer uploaded a proof of transfer
 * @param createdAt       instant on which the payment was created, in UTC
 */
public record PendingPayment(
        String paymentId,
        String userId,
        PaymentModality targetModality,
        String targetReference,
        BigDecimal amount,
        String currency,
        PaymentStatusType statusType,
        boolean hasProof,
        Instant createdAt) {
}
