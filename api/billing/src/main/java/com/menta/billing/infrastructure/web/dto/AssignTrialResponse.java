package com.menta.billing.infrastructure.web.dto;

import com.menta.billing.application.dto.TrialAssignmentResult;
import java.time.Instant;

/**
 * {@code 201 Created} body for an admin-assigned trial subscription (US-BILLING-012, design D3).
 *
 * <p>Deliberately excludes {@code reason} — the same structural-absence discipline {@link
 * CancelSubscriptionResponse} applies to {@code cancellationReason} (design.md D2 of #130): the
 * grant's reason is persisted and auditable, but never echoed back in this response shape.</p>
 */
public record AssignTrialResponse(
    String subscriptionId, String userId, String planId, String type, String status,
    Instant startDate, Instant endDate, int days
) {
    public static AssignTrialResponse from(TrialAssignmentResult result) {
        return new AssignTrialResponse(
            result.subscriptionId(), result.userId(), result.planId(), result.type().name(),
            result.status().name(), result.startDate(), result.endDate(), result.days()
        );
    }
}
