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

    /** Returns the shared decision that lets the request through; it carries no retry window. */
    public static RateLimitDecision allowed() {
        return ALLOWED;
    }

    /**
     * Creates a decision that refuses the request and tells the caller how long to wait.
     *
     * <p>A {@code null} or negative {@code retryAfter} is rejected with {@link
     * IllegalArgumentException}; zero is accepted.
     */
    public static RateLimitDecision limited(Duration retryAfter) {
        if (retryAfter == null || retryAfter.isNegative()) {
            throw new IllegalArgumentException("retryAfter must be a non-negative duration");
        }
        return new RateLimitDecision(false, retryAfter);
    }

    public boolean isAllowed() {
        return allowed;
    }

    /**
     * Returns how long the caller must wait before retrying a limited decision.
     *
     * @throws IllegalStateException if {@link #isAllowed()} is true — there is nothing to retry.
     */
    public Duration getRetryAfter() {
        if (allowed) {
            throw new IllegalStateException("an allowed decision has no retryAfter");
        }
        return retryAfter;
    }
}
