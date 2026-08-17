package com.menta.billing.application.dto;

import java.math.BigDecimal;
import java.util.List;

/** Full detail projection of a plan (US-BILLING-001 escenario 4). */
public record PlanDetailResult(
    String id,
    String name,
    String description,
    BigDecimal price,
    String currency,
    int durationDays,
    boolean featured,
    String termsAndConditions,
    String cancellationPolicy,
    List<PlanCourseResult> courses
) {
}
