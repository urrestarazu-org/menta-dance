package com.menta.billing.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;
import java.time.Duration;
import java.util.Objects;

/**
 * Thrown when the 60 req/min per-IP scraping budget for the plans endpoints
 * is exhausted (US-BILLING-001 — "Rate limit de 60 req/min por IP").
 */
public class PlanRateLimitedException extends BusinessException {

    private static final String ERROR_CODE = "PLAN_RATE_LIMITED";

    private final Duration retryAfter;

    public PlanRateLimitedException(Duration retryAfter) {
        super(ERROR_CODE, "Too many plan requests; retry later");
        this.retryAfter = Objects.requireNonNull(retryAfter, "retryAfter cannot be null");
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
