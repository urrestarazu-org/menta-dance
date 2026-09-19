package com.menta.billing.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;
import java.time.Duration;
import java.util.Objects;

/**
 * Thrown when the 10 bank-transfer subscription creations/user/day budget is exhausted (design
 * C7, US-BILLING-003). Distinct from {@link BillingDegradedException}: this is a spent budget, not
 * an unreachable Redis, so it maps to {@code 429} rather than {@code 503}.
 */
public class BankTransferRateLimitedException extends BusinessException {

    private static final String ERROR_CODE = "BANK_TRANSFER_RATE_LIMITED";

    private final Duration retryAfter;

    public BankTransferRateLimitedException(Duration retryAfter) {
        super(ERROR_CODE, "Too many bank-transfer subscription requests; retry later");
        this.retryAfter = Objects.requireNonNull(retryAfter, "retryAfter cannot be null");
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
