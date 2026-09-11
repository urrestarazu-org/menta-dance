package com.menta.billing.infrastructure.web.dto;

import com.menta.billing.application.dto.CurrentSubscriptionResult;
import java.time.Instant;

/**
 * {@code 200} body for {@code GET /api/v1/billing/subscriptions/me} (US-BILLING-004).
 *
 * <p>A single flat record discriminated by {@code status}, not one Jackson-polymorphic type per
 * state — no precedent for {@code @JsonTypeInfo} exists anywhere in {@code api/billing}
 * (design.md A4). The nullable set is bounded and documented per field, and every null serializes
 * as JSON {@code null} rather than being omitted, matching {@link SubscriptionCheckoutResponse}'s
 * own "null, not absent" precedent:</p>
 *
 * <ul>
 *   <li>{@code ACTIVE} — {@code startDate}, {@code endDate}, {@code daysRemaining} and {@code
 *   expiringSoon} are populated; {@code checkoutUrl} and {@code plansUrl} are {@code null}.</li>
 *   <li>{@code EXPIRED} — {@code startDate}/{@code endDate} are populated, {@code plansUrl} points
 *   at the plan catalog for renewal; {@code daysRemaining}, {@code expiringSoon} and {@code
 *   checkoutUrl} are {@code null}.</li>
 *   <li>{@code PENDING} — only {@code checkoutUrl} is populated; every date and computed field is
 *   {@code null}.</li>
 * </ul>
 */
public record CurrentSubscriptionResponse(
    String subscriptionId, String planId, String status, Instant startDate, Instant endDate,
    Long daysRemaining, Boolean expiringSoon, String checkoutUrl, String plansUrl
) {

    /** Sourced here, not duplicated — infrastructure is the only layer that knows its own routes. */
    public static final String PLANS_URL = "/api/v1/billing/plans";

    public static CurrentSubscriptionResponse from(CurrentSubscriptionResult result) {
        return switch (result) {
            case CurrentSubscriptionResult.Active active -> new CurrentSubscriptionResponse(
                active.subscriptionId(), active.planId(), "ACTIVE", active.startDate(), active.endDate(),
                active.daysRemaining(), active.expiringSoon(), null, null
            );
            case CurrentSubscriptionResult.Expired expired -> new CurrentSubscriptionResponse(
                expired.subscriptionId(), expired.planId(), "EXPIRED", expired.startDate(), expired.endDate(),
                null, null, null, PLANS_URL
            );
            case CurrentSubscriptionResult.PendingPayment pending -> new CurrentSubscriptionResponse(
                pending.subscriptionId(), pending.planId(), "PENDING", null, null,
                null, null, pending.checkoutUrl(), null
            );
        };
    }
}
