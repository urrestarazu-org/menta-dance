package com.menta.billing.infrastructure.web.dto;

import com.menta.billing.application.dto.PaymentStatusResult;
import java.time.Instant;

/**
 * {@code 200} body for {@code GET /api/v1/billing/payments/{id}} (#31, US-BILLING-003, design C9).
 *
 * <p>{@code updatedAt} is never a stored column — see {@link
 * com.menta.billing.domain.model.Payment#statusChangedAt()}'s Javadoc.</p>
 */
public record PaymentStatusResponse(String status, Instant createdAt, Instant updatedAt) {

    public static PaymentStatusResponse from(PaymentStatusResult result) {
        return new PaymentStatusResponse(result.status(), result.createdAt(), result.updatedAt());
    }
}
