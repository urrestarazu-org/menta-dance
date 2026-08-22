package com.menta.billing.infrastructure.web.dto;

import com.menta.billing.domain.model.PurchaseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * {@code selectedSessionId} is left unvalidated by Bean Validation on
 * purpose — whether it is required or forbidden depends on {@code
 * purchaseType}, a cross-field rule enforced in the use case ({@code
 * SelectedSessionRequiredException}/{@code SelectedSessionNotAllowedException},
 * US-BILLING-006).
 */
public record CreatePhysicalCourseQuoteRequest(
    @NotBlank String courseId,
    @NotNull PurchaseType purchaseType,
    String selectedSessionId
) {
}
