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
 *
 * <p>{@code bankTransferInstructions} is the bank-transfer flow's counterpart to {@code
 * checkoutUrl}/{@code providerPreferenceId} (US-BILLING-003 escenario 1, design D3): nullable,
 * mirroring the existing {@code overlapNotice} field, and populated only by {@link
 * com.menta.billing.application.usecase.CreateBankTransferSubscriptionUseCaseImpl} via {@link
 * #fromBankTransfer}.</p>
 */
public record SubscriptionCheckoutResult(
    String subscriptionId, String paymentId, String planId, SubscriptionStatus status,
    String providerPreferenceId, String checkoutUrl, String externalReference, OverlapNotice overlapNotice,
    BankTransferInstructions bankTransferInstructions
) {
    public static SubscriptionCheckoutResult from(
        Subscription subscription, String externalReference, OverlapNotice overlapNotice
    ) {
        return build(subscription, externalReference, overlapNotice, null);
    }

    /**
     * Bank-transfer checkouts never open a Mercado Pago preference and never compute an overlap
     * notice (design D3, C2) — only {@code bankTransferInstructions} is populated.
     */
    public static SubscriptionCheckoutResult fromBankTransfer(
        Subscription subscription, String externalReference, BankTransferInstructions bankTransferInstructions
    ) {
        return build(subscription, externalReference, null, bankTransferInstructions);
    }

    private static SubscriptionCheckoutResult build(
        Subscription subscription, String externalReference, OverlapNotice overlapNotice,
        BankTransferInstructions bankTransferInstructions
    ) {
        return new SubscriptionCheckoutResult(
            subscription.getId().toString(),
            subscription.getPaymentId().map(Object::toString).orElse(null),
            subscription.getPlanId().toString(),
            subscription.getStatus(),
            subscription.getProviderPreferenceId().orElse(null),
            subscription.getCheckoutUrl().orElse(null),
            externalReference,
            overlapNotice,
            bankTransferInstructions
        );
    }
}
