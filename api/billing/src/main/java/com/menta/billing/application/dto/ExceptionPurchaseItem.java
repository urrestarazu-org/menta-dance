package com.menta.billing.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One row of the admin EXCEPTION-purchases inbox (#237, design C5). {@code physicalSessionIds}
 * is {@code []}, never {@code null}, for a zero-session {@code EXCEPTION} row (D7) — this read
 * path never rehydrates a {@code Purchase}, so no domain invariant is exercised here.
 */
public record ExceptionPurchaseItem(
    UUID purchaseId, UUID paymentId, UUID userId, String targetModality, String targetReference,
    BigDecimal amount, String currency, Instant createdAt, List<String> physicalSessionIds
) {
}
