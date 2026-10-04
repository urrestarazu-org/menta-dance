package com.menta.bff.application.port.out;

import com.menta.bff.application.dto.ClassCheckout;
import com.menta.bff.application.dto.ClassPaymentMethod;
import com.menta.bff.application.dto.ClassPricing;
import com.menta.bff.application.dto.ClassPurchaseType;
import com.menta.bff.application.dto.ClassQuote;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Port for billing's in-person class endpoints: public pricing, and the
 * authenticated quote and checkout calls.
 *
 * <p>Expected business rejections each have their own exception so the
 * use cases can turn them into page messages; only transport failures map
 * to {@link ServiceUnavailableException} and the shared error view.
 * </p>
 */
public interface PhysicalPurchaseApiClient {

    /**
     * Reads a course's published pricing.
     *
     * <p>Calls GET /api/v1/billing/physical/courses/{courseId}/pricing with no
     * {@code Authorization} header (the endpoint is public).
     * </p>
     *
     * @param courseId course identifier
     * @return the pricing, or empty when none is published yet (404)
     * @throws ServiceUnavailableException if the upstream is unreachable or errors
     */
    Optional<ClassPricing> findPricing(String courseId);

    /**
     * Creates a quote for the caller.
     *
     * <p>Calls POST /api/v1/billing/physical/quotes with the caller's bearer token.
     * </p>
     *
     * @param courseId     course identifier
     * @param purchaseType month or single session
     * @param sessionId    session to buy for {@code INDIVIDUAL}, {@code null} for {@code MONTHLY}
     * @param accessToken  caller's access token; must not be {@code null}
     * @return the quote
     * @throws QuoteRejectedException      if billing refuses to quote (400/404/422)
     * @throws ServiceUnavailableException if the upstream is unreachable or errors
     */
    ClassQuote createQuote(String courseId, ClassPurchaseType purchaseType, String sessionId, String accessToken);

    /**
     * Checks out a quote for the caller.
     *
     * <p>Calls POST /api/v1/billing/physical/purchases with the caller's bearer
     * token. Billing replays the original checkout for a repeated
     * {@code idempotencyKey}.
     * </p>
     *
     * @param quoteId        quote to pay
     * @param paymentMethod  chosen payment rail
     * @param idempotencyKey key minted when the confirmation page was rendered
     * @param accessToken    caller's access token; must not be {@code null}
     * @return provider URL or transfer instructions
     * @throws CapacityUnavailableException if the class filled up (409)
     * @throws QuoteExpiredException        if the quote expired or does not exist (410)
     * @throws QuoteRejectedException       if billing rejects the request (400)
     * @throws ServiceUnavailableException  if the upstream is unreachable or errors
     */
    ClassCheckout checkout(
            String quoteId, ClassPaymentMethod paymentMethod, String idempotencyKey, String accessToken);

    /**
     * Thrown when billing refuses to quote or rejects a checkout request as invalid.
     */
    class QuoteRejectedException extends RuntimeException {
        public QuoteRejectedException(String message) {
            super(message);
        }
    }

    /**
     * Thrown when no spot is left for the quoted class (409).
     */
    class CapacityUnavailableException extends RuntimeException {
        public CapacityUnavailableException(String message) {
            super(message);
        }
    }

    /**
     * Thrown when the quote is past its validity window (410).
     */
    class QuoteExpiredException extends RuntimeException {
        public QuoteExpiredException(String message) {
            super(message);
        }
    }

    /**
     * Thrown when billing is unreachable, rate-limited or errors.
     */
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String message) {
            super(message);
        }

        public ServiceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
