package com.menta.billing.application.port.in;

import com.menta.billing.application.dto.CancelSubscriptionCommand;
import com.menta.billing.application.dto.CancellationResult;

/**
 * Application-layer entry point for cancelling a subscription's auto-renewal
 * (US-BILLING-011). Serves both the self-service (`/me`) and admin routes through one
 * authorization and transition path (design.md A4).
 */
public interface CancelSubscriptionUseCase {

    /**
     * @throws com.menta.billing.domain.exception.SubscriptionNotFoundException if no matching
     *     {@code ACTIVE} subscription is found, or the caller may not act on it.
     * @throws IllegalArgumentException if the admin route's {@code reason} is blank or absent.
     */
    CancellationResult cancel(CancelSubscriptionCommand command);
}
