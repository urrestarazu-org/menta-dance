package com.menta.billing.infrastructure.persistence.projection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPQL constructor-expression projection for the student's own payments (#767). Besides the
 * payment's own columns, including the stored {@code paymentMethod} (#771: the {@code PaymentMethod}
 * enum name, read as written), it carries what the same query resolves through its {@code LEFT JOIN}s:
 * the plan's {@code planName} for a virtual payment, the quote's {@code courseId} for a physical one
 * ({@code null} otherwise).
 */
public record OwnPaymentRow(
    UUID id, String statusType, BigDecimal expectedAmount, String expectedCurrency, String paymentMethod,
    String targetModality, String targetReference, Instant createdAt, String planName, String courseId
) {
}
