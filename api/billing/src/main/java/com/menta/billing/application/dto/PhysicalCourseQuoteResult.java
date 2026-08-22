package com.menta.billing.application.dto;

import com.menta.billing.domain.model.PurchaseType;
import com.menta.billing.domain.model.QuoteAvailability;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Read model returned by {@code CreatePhysicalCourseQuoteUseCase} (US-BILLING-006). */
public record PhysicalCourseQuoteResult(
    UUID id, String courseId, PurchaseType purchaseType, int scheduledSessionCount, String selectedSessionId,
    BigDecimal amount, String currency, QuoteAvailability availability, int pricingVersion, Instant createdAt,
    Instant expiresAt
) {
}
