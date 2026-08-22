package com.menta.billing.infrastructure.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * {@code currency} defaults to {@code "ARS"} when omitted — the project's
 * default currency (see {@code billing_plans.currency}'s DB default) — since
 * the user story's scenarios never mention a currency field explicitly.
 * {@code individualSurchargePercent}'s own {@code <= 0} check is a domain
 * rule (escenario 2, {@code InvalidIndividualSurchargeException}), not bean
 * validation — only its presence is validated here.
 */
public record UpdatePhysicalCoursePricingRequest(
    @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal monthlyPrice,
    String currency,
    @NotNull BigDecimal individualSurchargePercent,
    @NotBlank String reason
) {

    private static final String DEFAULT_CURRENCY = "ARS";

    public String currencyOrDefault() {
        return currency == null || currency.isBlank() ? DEFAULT_CURRENCY : currency;
    }
}
