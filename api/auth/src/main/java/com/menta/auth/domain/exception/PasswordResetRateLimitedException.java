package com.menta.auth.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;
import java.time.Duration;
import java.util.Objects;

/**
 * Thrown when either the forgot-password request budget (US-AUTH-005) or the
 * reset-password attempt budget (US-AUTH-006) is exhausted — 429 with
 * Retry-After, never revealing account existence.
 *
 * <p>Shared by both flows, mirroring how {@code ActivationRateLimitedException}
 * is reused across register and resend-activation: same failure shape, two
 * distinct rate limiter instances with their own budgets.</p>
 */
public class PasswordResetRateLimitedException extends BusinessException {

    private static final String ERROR_CODE = "PASSWORD_RESET_RATE_LIMITED";

    private final Duration retryAfter;

    public PasswordResetRateLimitedException(Duration retryAfter) {
        super(ERROR_CODE, "Too many password reset attempts; retry later");
        this.retryAfter = Objects.requireNonNull(retryAfter, "retryAfter cannot be null");
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
