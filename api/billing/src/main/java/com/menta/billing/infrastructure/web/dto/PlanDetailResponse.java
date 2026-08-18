package com.menta.billing.infrastructure.web.dto;

import com.menta.billing.application.dto.PlanDetailResult;
import java.math.BigDecimal;
import java.util.List;

/** Wire shape for a plan's full detail (US-BILLING-001 escenario 4). */
public record PlanDetailResponse(
    String id,
    String name,
    String description,
    BigDecimal price,
    String currency,
    int durationDays,
    boolean featured,
    String termsAndConditions,
    String cancellationPolicy,
    List<PlanCourseResponse> courses
) {

    public static PlanDetailResponse from(PlanDetailResult result) {
        return new PlanDetailResponse(
            result.id(), result.name(), result.description(), result.price(), result.currency(),
            result.durationDays(), result.featured(), result.termsAndConditions(),
            result.cancellationPolicy(),
            result.courses().stream().map(PlanCourseResponse::from).toList()
        );
    }
}
