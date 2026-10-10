package com.menta.bff.application.dto;

import java.math.BigDecimal;

/**
 * The published monthly price of a physical course, trimmed from the upstream
 * {@code PhysicalCoursePricingResponse} to what the class page shows. The individual-session
 * price is not here on purpose: it depends on the sessions of the period and Billing computes
 * it when quoting.
 *
 * @param courseId     course identifier
 * @param monthlyPrice price of the whole month
 * @param currency     ISO 4217 currency code
 */
public record PhysicalCoursePricing(String courseId, BigDecimal monthlyPrice, String currency) {
}
