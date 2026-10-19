package com.menta.billing.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of the admin pending-verification inbox (#33, US-BILLING-005, design C10).
 *
 * <p>{@code statusType} carries both status and substatus of the spec's requirement in one
 * field — the same discriminator {@code PaymentJpaEntity} already stores ({@code
 * AWAITING_MANUAL_VERIFICATION}). {@code targetReference} stands in for "plan": the payment's
 * target (subscription plan or physical course) the buyer is paying for.</p>
 */
public record PendingVerificationItem(
    UUID paymentId, UUID userId, String targetModality, String targetReference, BigDecimal amount,
    String currency, String statusType, boolean hasProof, Instant createdAt
) {
}
