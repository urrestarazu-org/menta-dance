package com.menta.billing.application.dto;

import com.menta.billing.domain.model.Money;
import java.util.Objects;

/**
 * What Billing asks the provider to open a checkout for (US-BILLING-010).
 *
 * <p>{@code externalReference} is <strong>our</strong> reference, generated
 * before this call and already persisted on the local {@code Payment}. It is
 * the only identifier shared with the provider at this point, and the one the
 * webhook flow later correlates against.</p>
 */
public record PaymentPreferenceRequest(String externalReference, String title, Money amount) {

    public PaymentPreferenceRequest {
        Objects.requireNonNull(amount, "amount cannot be null");
        if (externalReference == null || externalReference.isBlank()) {
            throw new IllegalArgumentException("externalReference cannot be null or blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title cannot be null or blank");
        }
    }
}
