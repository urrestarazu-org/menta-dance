package com.menta.auth.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;
import java.time.Duration;
import java.util.Objects;

/**
 * Thrown when register or resend-activation exceeds the configured
 * email/client fingerprint rate limit (auth-account-activation spec: "Rate
 * limiting" — 429 with Retry-After, never revealing account existence).
 */
public class ActivationRateLimitedException extends BusinessException {

    private static final String ERROR_CODE = "ACTIVATION_RATE_LIMITED";

    private final Duration retryAfter;

    public ActivationRateLimitedException(Duration retryAfter) {
        super(ERROR_CODE, "Too many activation attempts; retry later");
        this.retryAfter = Objects.requireNonNull(retryAfter, "retryAfter cannot be null");
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
