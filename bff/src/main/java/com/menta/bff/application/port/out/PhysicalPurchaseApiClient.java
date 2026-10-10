package com.menta.bff.application.port.out;

import com.menta.bff.application.dto.PhysicalPurchaseType;
import com.menta.bff.application.dto.PhysicalQuote;

/**
 * Port for the authenticated physical-class purchase endpoints of the Billing
 * API (so far, quotes). Kept apart from {@link BillingApiClient} on purpose: that port promises
 * its public plans call has no access-token parameter at all, and these calls
 * need one.
 *
 * <p>The method sends {@code Authorization: Bearer <accessToken>}; the token is
 * never logged. A 404 surfaces as {@link BillingApiClient.NotFoundException}; 429,
 * 5xx, an unexpected status and transport errors surface as
 * {@link BillingApiClient.ServiceUnavailableException}.
 * </p>
 */
public interface PhysicalPurchaseApiClient {

    /**
     * Creates a quote valid for one hour ({@code POST /api/v1/billing/physical/quotes}).
     *
     * @param accessToken       caller's access token
     * @param courseId          course to quote
     * @param purchaseType      monthly or individual
     * @param selectedSessionId session of an individual quote, {@code null} for monthly
     * @return the created quote
     * @throws NoScheduledSessionsException if the period has no sessions (422)
     * @throws InvalidRequestException      if the upstream rejects the request (400, other 422)
     */
    PhysicalQuote createQuote(
            String accessToken, String courseId, PhysicalPurchaseType purchaseType, String selectedSessionId);

    /** Base of the failures this port defines, so adapters can tell them from transport errors. */
    abstract class PurchaseFailureException extends RuntimeException {
        protected PurchaseFailureException(String message) {
            super(message);
        }
    }

    /** The quoted period has no scheduled sessions (422 {@code NO_SCHEDULED_SESSIONS}). */
    class NoScheduledSessionsException extends PurchaseFailureException {
        public NoScheduledSessionsException(String message) {
            super(message);
        }
    }

    /** The upstream rejected the request as malformed or inconsistent (400, or a 422 other than no sessions). */
    class InvalidRequestException extends PurchaseFailureException {
        public InvalidRequestException(String message) {
            super(message);
        }
    }
}
