package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.PhysicalPurchaseType;

/**
 * Use case that quotes a physical class for the signed-in student.
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface QuotePhysicalPurchaseUseCase {

    /**
     * Creates a quote and builds the page that shows it.
     *
     * @param accessToken       caller's access token
     * @param courseId          course to quote
     * @param purchaseType      monthly or individual
     * @param selectedSessionId session of an individual quote, {@code null} for monthly
     * @return the quote view
     * @throws com.menta.bff.application.port.out.CatalogApiClient.NotFoundException if the course is unknown
     * @throws com.menta.bff.application.port.out.PhysicalPurchaseApiClient.NoScheduledSessionsException
     *         if the period has no sessions
     * @throws com.menta.bff.application.port.out.PhysicalPurchaseApiClient.NoNewSessionsException
     *         if a monthly quote would add no session the student does not already own
     */
    PurchaseQuoteView execute(
            String accessToken, String courseId, PhysicalPurchaseType purchaseType, String selectedSessionId);
}
