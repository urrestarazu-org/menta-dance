package com.menta.physical.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;
import java.time.Duration;
import lombok.Getter;

/**
 * Thrown when a door reader (or whoever is presenting its credentials) has exhausted a QR check-in
 * rate-limit budget (ADR-0044). The HTTP layer maps it to 429 with a {@code Retry-After} header.
 */
@Getter
public class CheckInRateLimitedException extends BusinessException {

    private static final String ERROR_CODE = "CHECK_IN_RATE_LIMITED";

    private final Duration retryAfter;

    public CheckInRateLimitedException(Duration retryAfter) {
        super(ERROR_CODE, "Too many check-in attempts; retry later");
        if (retryAfter == null || retryAfter.isNegative()) {
            throw new IllegalArgumentException("retryAfter must be a non-negative duration");
        }
        this.retryAfter = retryAfter;
    }
}
