package com.menta.bff.application.port.out;

import com.menta.bff.application.dto.PaymentProgress;
import com.menta.bff.application.dto.PhysicalCheckout;
import com.menta.bff.application.dto.PhysicalPurchaseType;
import com.menta.bff.application.dto.PhysicalQuote;

/**
 * Port for the authenticated physical-class purchase endpoints of the Billing
 * API (quotes, the Mercado Pago checkout and the payment status). Kept apart from
 * {@link BillingApiClient} on purpose: that port promises its public plans call has no
 * access-token parameter at all, and these calls need one.
 *
 * <p>Every method sends {@code Authorization: Bearer <accessToken>}; the token is
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

    /**
     * Starts a Mercado Pago payment for a quote ({@code POST /api/v1/billing/physical/purchases}).
     * Repeating the same idempotency key returns the original checkout.
     *
     * @param accessToken    caller's access token
     * @param quoteId        quote to pay
     * @param idempotencyKey caller-chosen key, 1 to 128 characters
     * @return the payment id and the provider checkout URL
     * @throws CapacityUnavailableException if no room is left (409, no payment was created)
     * @throws QuoteExpiredException        if the quote expired or does not exist (410)
     * @throws InvalidRequestException      if the upstream rejects the request (400)
     */
    PhysicalCheckout createCheckout(String accessToken, String quoteId, String idempotencyKey);

    /**
     * Reads the status of a payment ({@code GET /api/v1/billing/payments/{paymentId}}). The
     * upstream answers 404 for a payment that is not the caller's (never 403), which is the
     * ownership check.
     *
     * @param accessToken caller's access token
     * @param paymentId   payment to read
     * @return where the payment stands; any pending or unknown upstream status reads as pending
     */
    PaymentProgress getPaymentProgress(String accessToken, String paymentId);

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

    /** No room left for the quote (409 {@code CAPACITY_UNAVAILABLE}); no payment was created. */
    class CapacityUnavailableException extends PurchaseFailureException {
        public CapacityUnavailableException(String message) {
            super(message);
        }
    }

    /** The quote expired or never existed (410 {@code PHYSICAL_COURSE_QUOTE_EXPIRED}). */
    class QuoteExpiredException extends PurchaseFailureException {
        public QuoteExpiredException(String message) {
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
