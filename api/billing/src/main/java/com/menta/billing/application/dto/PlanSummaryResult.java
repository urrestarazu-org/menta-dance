package com.menta.billing.application.dto;

import java.math.BigDecimal;
import java.util.List;

/** List-item projection of a plan (US-BILLING-001 escenario 1). */
public record PlanSummaryResult(
    String id,
    String name,
    String description,
    BigDecimal price,
    String currency,
    int durationDays,
    boolean featured,
    List<PlanCourseResult> courses
) {
}
