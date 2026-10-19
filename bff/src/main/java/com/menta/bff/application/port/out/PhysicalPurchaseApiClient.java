package com.menta.bff.application.port.out;

import com.menta.bff.application.dto.PaymentProofFile;
import com.menta.bff.application.dto.PaymentStatusView;
import com.menta.bff.application.dto.PhysicalBankTransfer;
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
     * @throws NoNewSessionsException       if the quote would add no session the student does not
     *                                      already own (409 NO_NEW_SESSIONS, nothing was persisted)
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
     * @throws NoNewSessionsException        if the purchase would add no session the student does not
     *                                       already own (409 NO_NEW_SESSIONS, nothing was persisted)
     * @throws QuoteExpiredException        if the quote expired or does not exist (410)
     * @throws InvalidRequestException      if the upstream rejects the request (400)
     */
    PhysicalCheckout createCheckout(String accessToken, String quoteId, String idempotencyKey);

    /**
     * Starts a bank transfer payment for a quote ({@code POST /api/v1/billing/physical/purchases}
     * with {@code BANK_TRANSFER}). Repeating the same idempotency key returns the original payment.
     * The payment waits for a manual verification: nothing is accredited here.
     *
     * @param accessToken    caller's access token
     * @param quoteId        quote to pay
     * @param idempotencyKey caller-chosen key, 1 to 128 characters
     * @return the payment id and the transfer instructions
     * @throws CapacityUnavailableException if no room is left (409)
     * @throws NoNewSessionsException       if the purchase would add no new session (409)
     * @throws QuoteExpiredException        if the quote expired or does not exist (410)
     * @throws InvalidRequestException      if the upstream rejects the request (400)
     */
    PhysicalBankTransfer createBankTransferCheckout(String accessToken, String quoteId, String idempotencyKey);

    /**
     * Uploads the proof of a transfer ({@code POST /api/v1/billing/payments/{paymentId}/proof},
     * multipart field {@code file}). The upstream answers 404 for a payment that is not the
     * caller's or no longer awaits verification.
     *
     * @param accessToken caller's access token
     * @param paymentId   bank transfer payment the proof belongs to
     * @param proof       the file
     * @throws ProofRejectedException if the upstream rejects the file (400)
     * @throws TooManyProofsException if the upload budget of the payment is spent (429)
     */
    void submitPaymentProof(String accessToken, String paymentId, PaymentProofFile proof);

    /**
     * Reads the status of a payment ({@code GET /api/v1/billing/payments/{paymentId}}). The
     * upstream answers 404 for a payment that is not the caller's (never 403), which is the
     * ownership check.
     *
     * @param accessToken caller's access token
     * @param paymentId   payment to read
     * @return where the payment stands (any pending or unknown status reads as pending) and its
     *         purchase fulfillment, {@code null} when absent or unknown
     */
    PaymentStatusView getPaymentProgress(String accessToken, String paymentId);

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

    /**
     * The purchase would add no session the student does not already own (409
     * {@code NO_NEW_SESSIONS}): every session a monthly would cover, or the selected session of an
     * individual purchase. Nothing was persisted, so no quote, hold or payment exists.
     */
    class NoNewSessionsException extends PurchaseFailureException {
        public NoNewSessionsException(String message) {
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

    /** The upstream rejected the proof file: wrong type, too big, empty or unreadable (400). */
    class ProofRejectedException extends PurchaseFailureException {
        public ProofRejectedException(String message) {
            super(message);
        }
    }

    /** The proof upload budget of the payment is spent (429). */
    class TooManyProofsException extends PurchaseFailureException {
        public TooManyProofsException(String message) {
            super(message);
        }
    }
}
