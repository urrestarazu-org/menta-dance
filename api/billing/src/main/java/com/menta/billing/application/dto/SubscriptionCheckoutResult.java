package com.menta.billing.application.dto;

import com.menta.billing.domain.model.Subscription;
import com.menta.billing.domain.model.SubscriptionStatus;

/**
 * What the checkout returns (US-BILLING-010 escenario 1): the subscription's
 * own identifier plus the provider URL the buyer must be sent to.
 *
 * <p>Three identifiers are involved in a Checkout Pro flow and none of them
 * substitute for another — {@code providerPreferenceId} names the preference,
 * {@code externalReference} is ours and is what correlates the later webhook,
 * and the provider's {@code payment.id} does not exist yet and is therefore
 * absent here.</p>
 */
public record SubscriptionCheckoutResult(
    String subscriptionId, String paymentId, String planId, SubscriptionStatus status,
    String providerPreferenceId, String checkoutUrl, String externalReference
) {
    public static SubscriptionCheckoutResult from(Subscription subscription, String externalReference) {
        return new SubscriptionCheckoutResult(
            subscription.getId().toString(),
            subscription.getPaymentId().toString(),
            subscription.getPlanId().toString(),
            subscription.getStatus(),
            subscription.getProviderPreferenceId().orElse(null),
            subscription.getCheckoutUrl().orElse(null),
            externalReference
        );
    }
}
