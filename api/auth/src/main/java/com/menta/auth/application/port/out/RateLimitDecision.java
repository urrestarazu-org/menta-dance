package com.menta.auth.application.port.out;

import java.time.Duration;
import java.util.Objects;

/**
 * Outcome of a rate-limit check (auth-account-activation spec: "Rate
 * limiting" — 429 with Retry-After, never revealing account existence).
 */
public final class RateLimitDecision {

    private static final RateLimitDecision ALLOWED = new RateLimitDecision(true, Duration.ZERO);

    private final boolean allowed;
    private final Duration retryAfter;

    private RateLimitDecision(boolean allowed, Duration retryAfter) {
        Objects.requireNonNull(retryAfter, "retryAfter cannot be null");
        if (retryAfter.isNegative()) {
            throw new IllegalArgumentException("retryAfter cannot be negative");
        }
        if (allowed && !retryAfter.isZero()) {
            throw new IllegalArgumentException("an allowed decision cannot carry a retryAfter");
        }
        if (!allowed && retryAfter.isZero()) {
            throw new IllegalArgumentException(
                "a limited decision must carry a positive retryAfter"
            );
        }
        this.allowed = allowed;
        this.retryAfter = retryAfter;
    }

    /**
     * Returns the decision that lets the request proceed.
     *
     * @return a shared instance whose retry delay is zero
     */
    public static RateLimitDecision allowed() {
        return ALLOWED;
    }

    /**
     * Returns the decision that rejects the request.
     *
     * @param retryAfter how long the caller must wait before trying again; must be positive
     * @return a decision that reports {@code retryAfter} to the client
     * @throws IllegalArgumentException when {@code retryAfter} is zero or negative
     * @throws NullPointerException when {@code retryAfter} is null
     */
    public static RateLimitDecision limited(Duration retryAfter) {
        return new RateLimitDecision(false, retryAfter);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
