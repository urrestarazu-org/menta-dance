package com.menta.billing.application.dto;

import com.menta.billing.domain.model.PurchaseType;
import java.util.UUID;

/**
 * Input for {@link com.menta.billing.application.port.in.CreatePhysicalCourseQuoteUseCase}
 * (US-BILLING-006). {@code selectedSessionId} is required only for {@link
 * PurchaseType#INDIVIDUAL} and forbidden for {@link PurchaseType#MONTHLY} —
 * validated in the use case, not here, since the rule spans two fields.
 *
 * <p>{@code userId} is the authenticated student asking for the quote. The
 * quote never records it; it only lets a MONTHLY quote check which sessions
 * the student already owns (#541).</p>
 */
public record CreatePhysicalCourseQuoteCommand(
    UUID userId, String courseId, PurchaseType purchaseType, String selectedSessionId
) {
}
