package com.menta.billing.infrastructure.persistence.projection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPQL constructor-expression projection for the admin EXCEPTION-purchases list query (#237,
 * design C2). {@code purchaseId} identifies the {@code billing_purchases} row itself; every other
 * field comes from the explicit entity join to {@code PaymentJpaEntity} — {@code
 * PurchaseJpaEntity} carries no JPA association to it.
 */
public record ExceptionPurchaseRow(
    UUID purchaseId, UUID paymentId, UUID userId, String targetModality, String targetReference,
    BigDecimal expectedAmount, String expectedCurrency, Instant createdAt
) {
}
