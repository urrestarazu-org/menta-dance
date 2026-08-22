package com.menta.billing.infrastructure.web.dto;

import com.menta.billing.application.dto.SubscriptionCheckoutResult;

/**
 * {@code 201 Created} body for a subscription checkout (US-BILLING-010
 * escenario 1).
 *
 * <p>Exposes the preference id and our external reference alongside the
 * checkout URL — three distinct identifiers, never interchangeable. The
 * provider's own {@code payment.id} is absent because it does not exist
 * yet.</p>
 */
public record SubscriptionCheckoutResponse(
    String subscriptionId, String paymentId, String planId, String status,
    String providerPreferenceId, String externalReference, String checkoutUrl
) {
    public static SubscriptionCheckoutResponse from(SubscriptionCheckoutResult result) {
        return new SubscriptionCheckoutResponse(
            result.subscriptionId(), result.paymentId(), result.planId(), result.status().name(),
            result.providerPreferenceId(), result.externalReference(), result.checkoutUrl()
        );
    }
}
