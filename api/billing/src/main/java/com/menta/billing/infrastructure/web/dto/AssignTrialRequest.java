package com.menta.billing.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Body of {@code POST /api/v1/admin/billing/subscriptions/trial} (US-BILLING-012).
 *
 * <p>{@code reason} is mandatory here (design.md D5), mirroring {@link CancelSubscriptionRequest}'s
 * discipline: bean validation rejects a blank or absent value with {@code 400} before the use
 * case runs, and no state changes. {@code days} must be positive — bean validation rejects an
 * absent (defaults to {@code 0}), zero, or negative value the same way; the plan's own duration
 * is never substituted for it (spec.md "A non-positive or absent days value is rejected").</p>
 */
public record AssignTrialRequest(
    @NotBlank String userId, @NotBlank String planId, @NotBlank String reason, @Positive int days
) {
}
