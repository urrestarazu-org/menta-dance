package com.menta.billing.application.dto;

import java.time.Duration;

/** Outcome of a rate-limit check: either allowed, or limited with a retry window. */
public final class RateLimitDecision {

    private static final RateLimitDecision ALLOWED = new RateLimitDecision(true, null);

    private final boolean allowed;
    private final Duration retryAfter;

    private RateLimitDecision(boolean allowed, Duration retryAfter) {
        this.allowed = allowed;
        this.retryAfter = retryAfter;
    }

    public static RateLimitDecision allowed() {
        return ALLOWED;
    }

    public static RateLimitDecision limited(Duration retryAfter) {
        if (retryAfter == null || retryAfter.isNegative()) {
            throw new IllegalArgumentException("retryAfter must be a non-negative duration");
        }
        return new RateLimitDecision(false, retryAfter);
    }

    public boolean isAllowed() {
        return allowed;
    }

    /** @throws IllegalStateException if {@link #isAllowed()} is true — there is nothing to retry. */
    public Duration getRetryAfter() {
        if (allowed) {
            throw new IllegalStateException("an allowed decision has no retryAfter");
        }
        return retryAfter;
    }
}
