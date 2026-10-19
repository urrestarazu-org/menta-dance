package com.menta.billing.application.dto;

import java.util.UUID;

/**
 * Input to {@link com.menta.billing.application.port.in.CancelSubscriptionUseCase}
 * (US-BILLING-011).
 *
 * <p>{@code reason} is mandatory only when cancelling on behalf of another user — enforced by
 * {@link com.menta.billing.domain.model.Subscription#cancel} — but the admin HTTP route always
 * supplies it, since {@code by} is never that route's subscription owner (design.md D1).
 */
public record CancelSubscriptionCommand(CancellationTarget target, UUID actingUserId, boolean isAdmin, String reason) {
}
