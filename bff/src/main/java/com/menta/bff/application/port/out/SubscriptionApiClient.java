package com.menta.bff.application.port.out;

import com.menta.bff.application.dto.CurrentSubscription;
import com.menta.bff.application.dto.SubscriptionCheckout;
import java.time.Instant;

/**
 * Port for the authenticated subscription endpoints of the Billing API. Kept apart from
 * {@link BillingApiClient} on purpose: that port promises its public plans call has no
 * access-token parameter at all, and these calls need one.
 *
 * <p>Every method sends {@code Authorization: Bearer <accessToken>}; the token is never logged.
 * Failures are closed: anything that is not a documented answer surfaces as
 * {@link BillingApiClient.ServiceUnavailableException}.
 * </p>
 */
public interface SubscriptionApiClient {

    /**
     * Reads the caller's current subscription ({@code GET /api/v1/billing/subscriptions/me}).
     *
     * @param accessToken caller's access token
     * @return the current subscription; {@link CurrentSubscription.None} on 404 {@code NO_SUBSCRIPTION}
     * @throws BillingApiClient.ServiceUnavailableException on any other outcome: 404 with another
     *         code, 401, 429, 5xx, an unexpected or incomplete answer, or a transport error
     */
    CurrentSubscription getCurrentSubscription(String accessToken);

    /**
     * Starts a Mercado Pago payment for a plan ({@code POST /api/v1/billing/subscriptions}).
     * Repeating the same idempotency key returns the original checkout.
     *
     * @param accessToken    caller's access token
     * @param planId         plan to subscribe to
     * @param idempotencyKey caller-chosen key, 1 to 128 characters
     * @return the payment id and the provider checkout URL
     * @throws SubscriptionAlreadyActiveException if the student already holds a subscription (409)
     * @throws SubscriptionConflictException      if the subscription changed concurrently (other 409)
     * @throws PlanNotAvailableException          if the plan cannot be subscribed to (422)
     * @throws InvalidRequestException            if the upstream rejects the request (400)
     * @throws BillingApiClient.ServiceUnavailableException on any other outcome, including 404 and
     *         an incomplete answer
     */
    SubscriptionCheckout createSubscriptionCheckout(String accessToken, String planId, String idempotencyKey);

    /** Base of the failures this port defines, so adapters can tell them from transport errors. */
    abstract class SubscriptionFailureException extends RuntimeException {
        protected SubscriptionFailureException(String message) {
            super(message);
        }
    }

    /**
     * The student already holds a subscription (409 {@code SUBSCRIPTION_ALREADY_ACTIVE}).
     */
    class SubscriptionAlreadyActiveException extends SubscriptionFailureException {

        private final transient Instant currentEndDate;

        /**
         * Creates the failure.
         *
         * @param message        diagnostic text, never shown to the student
         * @param currentEndDate end of the blocking subscription, {@code null} when it has none yet
         */
        public SubscriptionAlreadyActiveException(String message, Instant currentEndDate) {
            super(message);
            this.currentEndDate = currentEndDate;
        }

        /** End of the blocking subscription, or {@code null} when still pending payment. */
        public Instant currentEndDate() {
            return currentEndDate;
        }
    }

    /** The subscription changed while it was being processed (409 {@code SUBSCRIPTION_CONFLICT}). */
    class SubscriptionConflictException extends SubscriptionFailureException {
        public SubscriptionConflictException(String message) {
            super(message);
        }
    }

    /** The plan does not exist or is inactive (422 {@code PLAN_NOT_AVAILABLE}). */
    class PlanNotAvailableException extends SubscriptionFailureException {
        public PlanNotAvailableException(String message) {
            super(message);
        }
    }

    /** The upstream rejected the request as malformed or inconsistent (400, other 422). */
    class InvalidRequestException extends SubscriptionFailureException {
        public InvalidRequestException(String message) {
            super(message);
        }
    }
}
