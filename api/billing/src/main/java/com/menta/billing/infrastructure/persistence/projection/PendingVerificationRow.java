package com.menta.billing.infrastructure.persistence.projection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPQL constructor-expression projection for the pending-verification list query (#33,
 * US-BILLING-005, design C10). {@code proofCount} is the correlated {@code COUNT} subquery
 * against {@code billing_payment_proofs} — {@code > 0} maps to {@code hasProof} in {@code
 * PaymentRepositoryAdapter}, avoiding a row-multiplying join.
 */
public record PendingVerificationRow(
    UUID id, UUID userId, String targetModality, String targetReference, BigDecimal expectedAmount,
    String expectedCurrency, String statusType, Instant createdAt, Long proofCount
) {
}
