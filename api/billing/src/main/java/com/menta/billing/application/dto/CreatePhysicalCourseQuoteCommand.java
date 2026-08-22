package com.menta.billing.application.dto;

import com.menta.billing.domain.model.PurchaseType;

/**
 * Input for {@link com.menta.billing.application.port.in.CreatePhysicalCourseQuoteUseCase}
 * (US-BILLING-006). {@code selectedSessionId} is required only for {@link
 * PurchaseType#INDIVIDUAL} and forbidden for {@link PurchaseType#MONTHLY} —
 * validated in the use case, not here, since the rule spans two fields.
 */
public record CreatePhysicalCourseQuoteCommand(String courseId, PurchaseType purchaseType, String selectedSessionId) {
}
