package com.menta.billing.application.dto;

import java.math.BigDecimal;

/** Input for {@code UpdatePhysicalCoursePricingUseCase} (US-BILLING-009 escenario 1). */
public record UpdatePhysicalCoursePricingCommand(
    BigDecimal monthlyPrice, String currency, BigDecimal individualSurchargePercent, String reason
) {
}
