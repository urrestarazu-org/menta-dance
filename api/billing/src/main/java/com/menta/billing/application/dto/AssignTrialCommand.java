package com.menta.billing.application.dto;

import java.util.UUID;

/**
 * Input to {@link com.menta.billing.application.port.in.AssignTrialSubscriptionUseCase}
 * (US-BILLING-012).
 *
 * <p>{@code reason} and {@code days} are validated at the web layer ({@code @NotBlank}/{@code
 * @Positive} on the request DTO, Phase 4) — not here. {@link
 * com.menta.billing.domain.model.TrialGrant}'s canonical constructor is the last-resort guard if
 * this command is ever built with an invalid pair directly, without going through the route.</p>
 *
 * <p>{@code isAdmin} mirrors {@link CancelSubscriptionCommand}'s defense-in-depth discipline
 * (design A4): the route is already gated by {@code hasRole("ADMIN")}, so this is a second,
 * independent check inside the use case, not the sole authorization mechanism.</p>
 */
public record AssignTrialCommand(
    UUID userId, String planId, UUID actingUserId, boolean isAdmin, String reason, int days
) {
}
