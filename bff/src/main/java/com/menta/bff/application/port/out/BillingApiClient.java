package com.menta.bff.application.port.out;

import com.menta.bff.application.dto.PlanSummary;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/**
 * Port for communicating with the upstream billing plans endpoint. Part of
 * Clean Architecture application layer — abstracts HTTP/REST details.
 */
public interface BillingApiClient {

    /**
     * Retrieves the public plan catalog.
     * <p>
     * Calls GET /api/v1/billing/plans. Never sends an {@code Authorization}
     * header — the plans endpoint is public and caller-agnostic (every
     * visitor sees the same plans at the same prices), so this method takes
     * no access-token parameter at all: there is no parameter an implementer
     * could accidentally thread a token through (billing-api-integration
     * spec: "the plans call never carries an Authorization header").
     * </p>
     *
     * @return the list of plans, in upstream response order
     * @throws NotFoundException           if the upstream endpoint reports no plans found (404)
     * @throws ServiceUnavailableException if the upstream is unreachable, rate-limited, or errors (429/503)
     */
    List<PlanSummary> getPlans();

    /**
     * Exception thrown when the requested resource does not exist upstream (404).
     * <p>
     * {@code @ResponseStatus} lets an uncaught instance resolve through Spring
     * Boot's default {@code /error} → {@code error.html} path with no new
     * exception-handler class, so no upstream body or status detail ever
     * reaches the browser (spec: graceful degradation on upstream failure).
     * </p>
     */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }

        public NotFoundException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when the upstream is unavailable, rate-limits the BFF
     * (429), or errors (5xx), optionally carrying the {@code Retry-After}
     * value in seconds when the upstream provided one.
     * <p>
     * {@code @ResponseStatus} lets an uncaught instance resolve through Spring
     * Boot's default {@code /error} → {@code error.html} path with no new
     * exception-handler class; the {@code Retry-After} hint stays internal to
     * the BFF and is never exposed to the browser.
     * </p>
     */
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    class ServiceUnavailableException extends RuntimeException {

        private final Long retryAfterSeconds;

        public ServiceUnavailableException(String message) {
            super(message);
            this.retryAfterSeconds = null;
        }

        public ServiceUnavailableException(String message, Throwable cause) {
            super(message, cause);
            this.retryAfterSeconds = null;
        }

        public ServiceUnavailableException(String message, Long retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public Long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
