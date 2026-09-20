package com.menta.billing.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /api/v1/admin/billing/payments/{id}/reject} (#31, US-BILLING-003, design C1).
 *
 * <p>{@code reason} is mandatory — bean validation rejects a blank or absent value with {@code
 * 400} before the use case runs, mirroring {@code CancelSubscriptionRequest}.</p>
 */
public record RejectPaymentRequest(@NotBlank String reason) {
}
