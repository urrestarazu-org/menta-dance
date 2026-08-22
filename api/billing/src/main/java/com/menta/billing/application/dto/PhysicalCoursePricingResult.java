package com.menta.billing.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Read model returned by both {@code Get}/{@code UpdatePhysicalCoursePricingUseCase} (US-BILLING-009). */
public record PhysicalCoursePricingResult(
    String courseId, BigDecimal monthlyPrice, String currency, BigDecimal individualSurchargePercent, int version,
    Instant updatedAt
) {
}
