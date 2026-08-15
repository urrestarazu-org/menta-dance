package com.menta.auth.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;
import java.time.Duration;
import java.util.Objects;

/**
 * Thrown when login attempts exceed the configured email/client fingerprint
 * budget (US-AUTH-002: "intentos fallidos se limitan y auditan").
 *
 * <p>Maps to 429 with {@code Retry-After}. Distinct from
 * {@link LockedUserException} (423) on purpose: this condition is temporary,
 * expires on its own, and never reflects a change in account state.</p>
 */
public class LoginRateLimitedException extends BusinessException {

    private static final String ERROR_CODE = "LOGIN_RATE_LIMITED";

    private final Duration retryAfter;

    public LoginRateLimitedException(Duration retryAfter) {
        super(ERROR_CODE, "Too many login attempts; retry later");
        this.retryAfter = Objects.requireNonNull(retryAfter, "retryAfter cannot be null");
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
