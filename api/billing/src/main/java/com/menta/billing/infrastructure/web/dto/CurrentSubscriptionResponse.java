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
 *
 * <p>{@code type} (#766) is populated in every state with the {@code SubscriptionType} name —
 * {@code PAID} or {@code TRIAL} — so a client can tell an admin-granted trial from a paid
 * subscription. It was appended without touching any existing field.</p>
 */
public record CurrentSubscriptionResponse(
    String subscriptionId, String planId, String status, Instant startDate, Instant endDate,
    Long daysRemaining, Boolean expiringSoon, String checkoutUrl, String plansUrl, String type
) {

    /** Sourced here, not duplicated — infrastructure is the only layer that knows its own routes. */
    public static final String PLANS_URL = "/api/v1/billing/plans";

    /**
     * Maps the current-subscription variant to the flat response, discriminated by the {@code
     * status} string.
     *
     * <p>Only the fields of the matching variant are filled and the rest are {@code null}; an
     * expired subscription also gets the plan catalog URL so that the client can offer a renewal.
     */
    public static CurrentSubscriptionResponse from(CurrentSubscriptionResult result) {
        return switch (result) {
            case CurrentSubscriptionResult.Active active -> new CurrentSubscriptionResponse(
                active.subscriptionId(), active.planId(), "ACTIVE", active.startDate(), active.endDate(),
                active.daysRemaining(), active.expiringSoon(), null, null, active.type().name()
            );
            case CurrentSubscriptionResult.Expired expired -> new CurrentSubscriptionResponse(
                expired.subscriptionId(), expired.planId(), "EXPIRED", expired.startDate(), expired.endDate(),
                null, null, null, PLANS_URL, expired.type().name()
            );
            case CurrentSubscriptionResult.PendingPayment pending -> new CurrentSubscriptionResponse(
                pending.subscriptionId(), pending.planId(), "PENDING", null, null,
                null, null, pending.checkoutUrl(), null, pending.type().name()
            );
        };
    }
}
