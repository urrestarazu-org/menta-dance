package com.menta.billing.application.port.in;

import com.menta.billing.application.dto.CreateSubscriptionCheckoutCommand;
import com.menta.billing.application.dto.SubscriptionCheckoutResult;

/** Entry port for {@code POST /api/v1/billing/subscriptions} (US-BILLING-010). */
public interface CreateSubscriptionCheckoutUseCase {

    /**
     * Opens a Checkout Pro subscription checkout.
     *
     * <p>Only {@code MERCADO_PAGO} is accepted; any other method fails with {@link
     * IllegalArgumentException}. A replay of the same user and idempotency key returns the existing
     * subscription's checkout instead of a conflict. Otherwise the plan must be active and accept
     * the method, and the user must hold no current subscription ({@code
     * SubscriptionAlreadyActiveException}). The local payment and subscription are saved before the
     * provider is asked for a preference, and a provider failure surfaces as {@code
     * PaymentPreferenceUnavailableException}.
     */
    SubscriptionCheckoutResult create(CreateSubscriptionCheckoutCommand command);
}
