package com.menta.billing.infrastructure.web.dto;

import com.menta.billing.application.dto.PaymentStatusResult;
import java.time.Instant;

/**
 * {@code 200} body for {@code GET /api/v1/billing/payments/{id}} (#31, US-BILLING-003, design C9).
 *
 * <p>{@code updatedAt} is never a stored column — see {@link
 * com.menta.billing.domain.model.Payment#statusChangedAt()}'s Javadoc.</p>
 *
 * <p>{@code fulfillment} (#559) is the Purchase status name only, or an always-serialized {@code
 * null}. Resolution type, note and exception reason are deliberately never exposed.</p>
 */
public record PaymentStatusResponse(String status, Instant createdAt, Instant updatedAt, String fulfillment) {

    /** Maps the status result to the wire shape without altering any value. */
    public static PaymentStatusResponse from(PaymentStatusResult result) {
        String fulfillment = result.fulfillment() == null ? null : result.fulfillment().name();
        return new PaymentStatusResponse(result.status(), result.createdAt(), result.updatedAt(), fulfillment);
    }
}
