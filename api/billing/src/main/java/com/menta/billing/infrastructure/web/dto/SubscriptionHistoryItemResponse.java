package com.menta.billing.infrastructure.web.dto;

import com.menta.billing.application.dto.SubscriptionHistoryEntry;
import java.time.Instant;

/**
 * One entry of the {@code 200} body for {@code GET /api/v1/billing/subscriptions/me/history}
 * (US-BILLING-004). Renders id/plan/status/dates only — never courses, mirroring {@link
 * SubscriptionHistoryEntry}'s own deliberate projection shape.
 *
 * <p>{@code type} (#766) is the {@code SubscriptionType} name — {@code PAID} or {@code TRIAL} —
 * appended without touching any existing field.</p>
 */
public record SubscriptionHistoryItemResponse(
    String id, String planId, String status, Instant startDate, Instant endDate, String type
) {
    /** Maps one history entry to the wire shape, rendering the status and the type by enum name. */
    public static SubscriptionHistoryItemResponse from(SubscriptionHistoryEntry entry) {
        return new SubscriptionHistoryItemResponse(
            entry.id(), entry.planId(), entry.status().name(), entry.startDate(), entry.endDate(),
            entry.type().name()
        );
    }
}
