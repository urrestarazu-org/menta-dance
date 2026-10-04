package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ClassPurchaseType;

/**
 * Use case for quoting an in-person class purchase before payment.
 */
public interface QuoteClassUseCase {

    /**
     * Requests a quote for the caller and decides what the confirmation page shows.
     *
     * @param courseId     course identifier
     * @param purchaseType month or single session
     * @param sessionId    session for {@code INDIVIDUAL}, ignored for {@code MONTHLY}
     * @param accessToken  caller's access token
     * @return payable, sold out, or rejected
     * @throws com.menta.bff.application.port.out.PhysicalPurchaseApiClient.ServiceUnavailableException
     *         if billing is unreachable
     */
    ClassQuoteView execute(String courseId, ClassPurchaseType purchaseType, String sessionId, String accessToken);
}
