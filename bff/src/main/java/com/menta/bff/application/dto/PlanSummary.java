package com.menta.bff.application.dto;

import java.math.BigDecimal;

/**
 * Plan summary, trimmed from upstream's {@code PlanSummaryResult} to what the
 * plans page renders. {@code courses} is dropped — nothing on this page
 * displays it, and a future plan-detail page (out of scope here) would need
 * its own richer DTO anyway.
 *
 * @param id           plan identifier
 * @param name         plan name
 * @param description  plan description
 * @param price        plan price
 * @param currency     price currency code
 * @param durationDays plan duration in days
 * @param featured     whether the plan is highlighted on the page
 */
public record PlanSummary(
        String id,
        String name,
        String description,
        BigDecimal price,
        String currency,
        int durationDays,
        boolean featured) {
}
