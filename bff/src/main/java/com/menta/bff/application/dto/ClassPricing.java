package com.menta.bff.application.dto;

import java.math.BigDecimal;

/**
 * Published pricing of an in-person course, trimmed from billing's pricing
 * response to what the class page renders.
 *
 * @param monthlyPrice               price of a whole calendar month
 * @param currency                   currency code
 * @param individualSurchargePercent surcharge applied to a single-session purchase
 */
public record ClassPricing(BigDecimal monthlyPrice, String currency, BigDecimal individualSurchargePercent) {
}
