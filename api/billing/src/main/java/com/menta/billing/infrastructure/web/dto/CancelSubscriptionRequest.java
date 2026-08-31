package com.menta.billing.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code DELETE /api/v1/admin/billing/subscriptions/{subscriptionId}} (US-BILLING-011).
 *
 * <p>{@code reason} is mandatory here (design.md D1) — bean validation rejects a blank or
 * absent value with {@code 400} before the use case runs, and no state changes.</p>
 */
public record CancelSubscriptionRequest(@NotBlank String reason) {
}
