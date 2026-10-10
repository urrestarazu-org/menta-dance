package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.PhysicalCheckout;

/**
 * Use case that starts the Mercado Pago payment of a quote.
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface PayPhysicalPurchaseUseCase {

    /**
     * Starts the checkout. Repeating the same idempotency key returns the original checkout.
     *
     * @param accessToken    caller's access token
     * @param quoteId        quote to pay
     * @param idempotencyKey key identifying this attempt, 1 to 128 characters
     * @return the payment id and the provider checkout URL
     * @throws com.menta.bff.application.port.out.PhysicalPurchaseApiClient.CapacityUnavailableException
     *         if no room is left
     * @throws com.menta.bff.application.port.out.PhysicalPurchaseApiClient.QuoteExpiredException
     *         if the quote expired or does not exist
     */
    PhysicalCheckout execute(String accessToken, String quoteId, String idempotencyKey);
}
