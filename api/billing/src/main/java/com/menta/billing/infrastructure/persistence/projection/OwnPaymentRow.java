package com.menta.billing.infrastructure.persistence.projection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPQL constructor-expression projection for the student's own payments (#767). Besides the
 * payment's own columns it carries what the same query resolves through its {@code LEFT JOIN}s:
 * the subscription's {@code providerPreferenceId} and the plan's {@code planName} for a virtual
 * payment, the quote's {@code courseId} for a physical one ({@code null} otherwise).
 * {@code expectedExternalReference} and {@code providerPreferenceId} only feed the payment method
 * {@code PaymentRepositoryAdapter} derives; neither leaves the adapter.
 */
public record OwnPaymentRow(
    UUID id, String statusType, BigDecimal expectedAmount, String expectedCurrency,
    String expectedExternalReference, String targetModality, String targetReference, Instant createdAt,
    String providerPreferenceId, String planName, String courseId
) {
}
