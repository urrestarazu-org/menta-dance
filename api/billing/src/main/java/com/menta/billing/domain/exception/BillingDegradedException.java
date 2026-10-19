package com.menta.billing.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when the plans rate limiter cannot reach Redis (US-BILLING-001).
 * Fail-closed: an unavailable throttle over a public, unauthenticated,
 * scrapeable endpoint must refuse the request, not silently allow unlimited
 * traffic through. The HTTP layer maps this to 503 with Retry-After.
 */
public class BillingDegradedException extends BusinessException {

    private static final String ERROR_CODE = "BILLING_DEGRADED";

    public BillingDegradedException(Throwable cause) {
        super(ERROR_CODE, "Billing is temporarily unavailable; retry after 30s", cause);
    }
}
