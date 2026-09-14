package com.menta.billing.infrastructure.web.dto;

import com.menta.billing.application.dto.PhysicalPurchaseCheckoutResult;

/**
 * {@code 201 Created} body for a physical purchase checkout (#41,
 * US-PHYSICAL-004).
 *
 * <p>Exposes the preference id and our external reference alongside the
 * checkout URL — three distinct identifiers, never interchangeable, same
 * shape as {@code SubscriptionCheckoutResponse}. The provider's own {@code
 * payment.id} is absent because it does not exist yet.</p>
 */
public record PhysicalPurchaseCheckoutResponse(
    String paymentId, String quoteId, String status, String providerPreferenceId, String checkoutUrl,
    String externalReference
) {
    public static PhysicalPurchaseCheckoutResponse from(PhysicalPurchaseCheckoutResult result) {
        return new PhysicalPurchaseCheckoutResponse(
            result.paymentId(), result.quoteId(), result.status(), result.providerPreferenceId(),
            result.checkoutUrl(), result.externalReference()
        );
    }
}
