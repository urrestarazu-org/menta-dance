package com.menta.billing.infrastructure.web.dto;

import com.menta.billing.application.dto.PlanSummaryResult;
import java.math.BigDecimal;
import java.util.List;

/** Wire shape for a plan list item (US-BILLING-001 escenario 1/2). */
public record PlanSummaryResponse(
    String id,
    String name,
    String description,
    BigDecimal price,
    String currency,
    int durationDays,
    boolean featured,
    List<PlanCourseResponse> courses
) {

    /**
     * Maps a plan list item to the wire shape and converts each included course; the terms and the
     * cancellation policy are not part of the summary.
     */
    public static PlanSummaryResponse from(PlanSummaryResult result) {
        return new PlanSummaryResponse(
            result.id(), result.name(), result.description(), result.price(), result.currency(),
            result.durationDays(), result.featured(),
            result.courses().stream().map(PlanCourseResponse::from).toList()
        );
    }
}
