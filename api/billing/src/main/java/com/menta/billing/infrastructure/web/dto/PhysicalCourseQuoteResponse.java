package com.menta.billing.infrastructure.web.dto;

import com.menta.billing.application.dto.PhysicalCourseQuoteResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Wire shape for a freshly created physical course quote (US-BILLING-006). */
public record PhysicalCourseQuoteResponse(
    UUID id, String courseId, String purchaseType, int scheduledSessionCount, String selectedSessionId,
    BigDecimal amount, String currency, String availability, int pricingVersion, Instant createdAt,
    Instant expiresAt
) {

    public static PhysicalCourseQuoteResponse from(PhysicalCourseQuoteResult result) {
        return new PhysicalCourseQuoteResponse(
            result.id(), result.courseId(), result.purchaseType().name(), result.scheduledSessionCount(),
            result.selectedSessionId(), result.amount(), result.currency(), result.availability().name(),
            result.pricingVersion(), result.createdAt(), result.expiresAt()
        );
    }
}
