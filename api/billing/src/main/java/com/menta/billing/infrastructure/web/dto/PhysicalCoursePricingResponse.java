package com.menta.billing.infrastructure.web.dto;

import com.menta.billing.application.dto.PhysicalCoursePricingResult;
import java.math.BigDecimal;
import java.time.Instant;

/** Wire shape for a physical course's current pricing (US-BILLING-009). */
public record PhysicalCoursePricingResponse(
    String courseId, BigDecimal monthlyPrice, String currency, BigDecimal individualSurchargePercent, int version,
    Instant updatedAt
) {

    public static PhysicalCoursePricingResponse from(PhysicalCoursePricingResult result) {
        return new PhysicalCoursePricingResponse(
            result.courseId(), result.monthlyPrice(), result.currency(), result.individualSurchargePercent(),
            result.version(), result.updatedAt()
        );
    }
}
