package com.menta.bff.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A payment as the administrator's detail reads it ({@code PaymentDetailResponse} in {@code billing-v1.yaml}).
 * The signed proof URL of the API is deliberately not kept: it must never reach a view or the HTML, so only
 * whether a proof exists is exposed.
 *
 * @param paymentId       payment identifier
 * @param userId          identifier of the buyer
 * @param targetModality  whether the payment buys a physical course or a virtual plan
 * @param targetReference reference of what is bought: a plan or a physical course
 * @param amount          amount of the payment
 * @param currency        ISO 4217 currency code of the amount
 * @param statusType      where the payment stands
 * @param createdAt       instant on which the payment was created, in UTC
 * @param hasProof        whether the buyer uploaded a proof of transfer
 */
public record PaymentDetail(
        String paymentId,
        String userId,
        PaymentModality targetModality,
        String targetReference,
        BigDecimal amount,
        String currency,
        PaymentStatusType statusType,
        Instant createdAt,
        boolean hasProof) {
}
