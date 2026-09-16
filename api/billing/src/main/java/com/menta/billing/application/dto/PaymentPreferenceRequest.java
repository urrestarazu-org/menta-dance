package com.menta.billing.application.dto;

import com.menta.billing.domain.model.Money;
import java.time.Instant;
import java.util.Objects;

/**
 * What Billing asks the provider to open a checkout for (US-BILLING-010).
 *
 * <p>{@code externalReference} is <strong>our</strong> reference, generated
 * before this call and already persisted on the local {@code Payment}. It is
 * the only identifier shared with the provider at this point, and the one the
 * webhook flow later correlates against.</p>
 *
 * <p>{@code expiresAt} is nullable and provider-neutral (#208 design B6): the
 * physical checkout flow populates it from its capacity hold's
 * {@code expires_at}, never later than the moment the hold itself lapses.
 * The virtual subscription flow has no hold and always leaves it {@code
 * null} — an absent deadline is today's behaviour, not a regression, and
 * every {@link com.menta.billing.application.port.out.PaymentPreferencePort}
 * adapter must send no expiration at all when it is null rather than
 * inventing one.</p>
 */
public record PaymentPreferenceRequest(String externalReference, String title, Money amount, Instant expiresAt) {

    public PaymentPreferenceRequest {
        Objects.requireNonNull(amount, "amount cannot be null");
        if (externalReference == null || externalReference.isBlank()) {
            throw new IllegalArgumentException("externalReference cannot be null or blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title cannot be null or blank");
        }
    }

    /** Convenience for callers with no expiry concept (e.g. virtual subscriptions). */
    public PaymentPreferenceRequest(String externalReference, String title, Money amount) {
        this(externalReference, title, amount, null);
    }
}
