package com.menta.bff.application.port.out;

import com.menta.bff.application.dto.CorrectionDecision;
import com.menta.bff.application.dto.PaymentDetail;
import com.menta.bff.application.dto.PaymentProof;
import com.menta.bff.application.dto.PendingPaymentPage;

/**
 * Port for the administrator's manual payment verification (#370): the calls of the Billing API under
 * {@code /api/v1/admin/billing/payments}. It is a different port from {@link BillingApiClient}, which is public
 * and carries no token on purpose.
 *
 * <p>The calls are authenticated with the administrator's access token, which only ever travels in the
 * {@code Authorization} header: it is never logged nor put in an exception message. Neither are identifiers,
 * reasons, evidence or any response or error body. The API decides which state transitions are legal
 * (ADR-0038): approving, rejecting and correcting are not idempotent, so repeating one answers
 * {@code ILLEGAL_PAYMENT_STATE_TRANSITION}.
 * </p>
 *
 * <p>Every method throws {@link PaymentRefusalException} when the API refuses the request with one of the
 * expected codes, and {@link ServiceUnavailableException} for every other failure: timeout, transport error,
 * 401, 403, 429, 5xx, an unexpected status or code, or an unusable body.
 * </p>
 */
public interface AdminBillingApiClient {

    /**
     * Lists the payments awaiting manual verification, oldest first
     * ({@code GET /payments?status=PENDING&substatus=AWAITING_MANUAL_VERIFICATION}).
     *
     * @param accessToken caller's access token
     * @param page        zero-based page index
     * @param size        page size, at most 50: the API rejects a larger one instead of cutting it
     * @return the requested page; past the last page it has no items but carries the totals
     */
    PendingPaymentPage listPendingPayments(String accessToken, int page, int size);

    /**
     * Reads one payment ({@code GET /payments/{paymentId}}).
     *
     * @param accessToken caller's access token
     * @param paymentId   identifier of the payment, already validated as a UUID by the caller
     * @return the payment; whether it has a proof, never the proof's signed URL
     */
    PaymentDetail getPaymentDetail(String accessToken, String paymentId);

    /**
     * Fetches the transfer proof of a payment: asks the detail for its signed link and follows it, inside the
     * adapter, so neither the link nor its token ever leave it. The proof endpoint is not an admin one: the
     * signed token is its credential, so that second call carries no bearer token.
     *
     * @param accessToken caller's access token
     * @param paymentId   identifier of the payment, already validated as a UUID by the caller
     * @return the proof's bytes and its declared type, one of {@code image/png}, {@code image/jpeg} or
     *         {@code application/pdf}
     * @throws PaymentRefusalException with {@code PAYMENT_NOT_FOUND} when the payment does not exist, or
     *         {@code PROOF_NOT_FOUND} when it has no proof
     * @throws ServiceUnavailableException also when the link is not the expected relative one (the BFF never
     *         follows an arbitrary URL), the proof call fails, or its type is not allowed
     */
    PaymentProof fetchPaymentProof(String accessToken, String paymentId);

    /**
     * Approves a payment awaiting manual verification ({@code POST /payments/{paymentId}/approve}, 200 without
     * body).
     *
     * @param accessToken caller's access token
     * @param paymentId   identifier of the payment, already validated as a UUID by the caller
     */
    void approvePayment(String accessToken, String paymentId);

    /**
     * Rejects a payment awaiting manual verification ({@code POST /payments/{paymentId}/reject}, 200 without
     * body). The API mails the reason to the buyer.
     *
     * @param accessToken caller's access token
     * @param paymentId   identifier of the payment, already validated as a UUID by the caller
     * @param reason      why it is rejected, already trimmed and not blank
     */
    void rejectPayment(String accessToken, String paymentId, String reason);

    /**
     * Imposes an outcome on a payment that needs reconciliation ({@code POST /payments/{paymentId}/corrections},
     * 200 without body). It only applies to a payment in {@code RECONCILIATION_REQUIRED}.
     *
     * @param accessToken caller's access token
     * @param paymentId   identifier of the payment, already validated as a UUID by the caller
     * @param decision    the outcome to impose
     * @param reason      why the correction is made, already trimmed and not blank
     * @param evidence    verifiable evidence for it, already trimmed and not blank
     */
    void correctPayment(String accessToken, String paymentId, CorrectionDecision decision, String reason,
            String evidence);

    /**
     * The API refused the request with a code the screens word: {@code INVALID_REQUEST} (400),
     * {@code PAYMENT_NOT_FOUND} (404) or {@code ILLEGAL_PAYMENT_STATE_TRANSITION} (409). Anything else is a
     * {@link ServiceUnavailableException}. Its message is for logs only.
     */
    class PaymentRefusalException extends RuntimeException {

        /** The codes of {@code Problem.code} the API answers on these endpoints. */
        public enum Code {
            /** 400: an id that is not a UUID, a blank reason or evidence, or an unreadable body. */
            INVALID_REQUEST,
            /** 404: the payment does not exist. */
            PAYMENT_NOT_FOUND,
            /** The payment exists but has no transfer proof. */
            PROOF_NOT_FOUND,
            /** 409: the payment is not in a state that admits the operation, for instance it was already resolved. */
            ILLEGAL_PAYMENT_STATE_TRANSITION
        }

        private final Code code;

        public PaymentRefusalException(Code code) {
            super("Billing API refused the request: " + code);
            this.code = code;
        }

        public Code getCode() {
            return code;
        }
    }

    /** The payments could not be obtained or changed. Its message is for logs only. */
    class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String message) {
            super(message);
        }

        public ServiceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
