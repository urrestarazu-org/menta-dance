package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ClassPaymentMethod;

/**
 * Use case for paying an in-person class quote.
 */
public interface PurchaseClassUseCase {

    /**
     * Checks out the quote and decides where the browser goes next.
     *
     * @param courseId       course of the quote, only used for back links
     * @param quoteId        quote to pay
     * @param paymentMethod  chosen payment rail
     * @param idempotencyKey key minted with the confirmation page
     * @param accessToken    caller's access token
     * @return provider redirect, transfer instructions, sold out, or quote expired
     * @throws com.menta.bff.application.port.out.PhysicalPurchaseApiClient.ServiceUnavailableException
     *         if billing is unreachable or answers a checkout with no way to pay
     */
    ClassPurchaseView execute(
            String courseId, String quoteId, ClassPaymentMethod paymentMethod, String idempotencyKey,
            String accessToken);
}
