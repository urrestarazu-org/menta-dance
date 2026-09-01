package com.menta.billing.infrastructure.web.dto;

import com.menta.billing.application.dto.OverlapNotice;
import com.menta.billing.application.dto.SubscriptionCheckoutResult;

/**
 * {@code 201 Created} body for a subscription checkout (US-BILLING-010
 * escenario 1).
 *
 * <p>Exposes the preference id and our external reference alongside the
 * checkout URL — three distinct identifiers, never interchangeable. The
 * provider's own {@code payment.id} is absent because it does not exist
 * yet.</p>
 *
 * <p>{@code overlapNotice} is {@code null}, not absent, when the checkout does not overlap a
 * still-in-force cancellation for the same plan (US-BILLING-011 D3) — unlike {@code
 * CancelSubscriptionResponse}'s structurally absent {@code cancellationReason}, this field is an
 * ordinary nullable value the buyer is meant to see either way.</p>
 */
public record SubscriptionCheckoutResponse(
    String subscriptionId, String paymentId, String planId, String status,
    String providerPreferenceId, String externalReference, String checkoutUrl, OverlapNotice overlapNotice
) {
    public static SubscriptionCheckoutResponse from(SubscriptionCheckoutResult result) {
        return new SubscriptionCheckoutResponse(
            result.subscriptionId(), result.paymentId(), result.planId(), result.status().name(),
            result.providerPreferenceId(), result.externalReference(), result.checkoutUrl(),
            result.overlapNotice()
        );
    }
}
