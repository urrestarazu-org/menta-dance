package com.menta.billing.application.dto;

import java.util.Objects;

/**
 * The provider's answer to a preference creation (US-BILLING-010).
 *
 * <p>Note what is <em>not</em> here: the provider's {@code payment.id}.
 * Checkout Pro has none to give at this point — it only exists once the buyer
 * pays — which is exactly why {@code Payment.providerPaymentId} is bound
 * later, by the webhook flow.</p>
 *
 * @param preferenceId the provider's preference identifier — never confused
 *     with a payment id
 * @param checkoutUrl the {@code init_point} the buyer is redirected to
 */
public record PaymentPreferenceResult(String preferenceId, String checkoutUrl) {

    public PaymentPreferenceResult {
        Objects.requireNonNull(preferenceId, "preferenceId cannot be null");
        Objects.requireNonNull(checkoutUrl, "checkoutUrl cannot be null");
    }
}
