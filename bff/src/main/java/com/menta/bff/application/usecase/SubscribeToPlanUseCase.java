package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.SubscriptionCheckout;

/**
 * Use case that starts the Mercado Pago payment of a subscription plan.
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface SubscribeToPlanUseCase {

    /**
     * Starts the checkout. Repeating the same idempotency key returns the original checkout.
     *
     * @param accessToken    caller's access token
     * @param planId         plan to subscribe to
     * @param idempotencyKey key identifying this attempt, 1 to 128 characters
     * @return the payment id and the provider checkout URL
     * @throws com.menta.bff.application.port.out.SubscriptionApiClient.SubscriptionFailureException
     *         on a documented business failure (already active, conflict, plan unavailable, invalid)
     */
    SubscriptionCheckout execute(String accessToken, String planId, String idempotencyKey);
}
