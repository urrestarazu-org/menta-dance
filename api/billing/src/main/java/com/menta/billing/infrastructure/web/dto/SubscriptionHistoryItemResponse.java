package com.menta.billing.infrastructure.web.dto;

import com.menta.billing.application.dto.SubscriptionHistoryEntry;
import java.time.Instant;

/**
 * One entry of the {@code 200} body for {@code GET /api/v1/billing/subscriptions/me/history}
 * (US-BILLING-004). Renders id/plan/status/dates only — never courses, mirroring {@link
 * SubscriptionHistoryEntry}'s own deliberate projection shape.
 */
public record SubscriptionHistoryItemResponse(
    String id, String planId, String status, Instant startDate, Instant endDate
) {
    public static SubscriptionHistoryItemResponse from(SubscriptionHistoryEntry entry) {
        return new SubscriptionHistoryItemResponse(
            entry.id(), entry.planId(), entry.status().name(), entry.startDate(), entry.endDate()
        );
    }
}
