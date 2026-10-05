package com.menta.physical.application.dto;

import java.time.Duration;

/** Outcome of a check-in rate-limit check: allowed, or limited by one dimension with a retry window. */
public final class RateLimitDecision {

    private static final RateLimitDecision ALLOWED = new RateLimitDecision(true, null, null);

    private final boolean allowed;
    private final RateLimitDimension dimension;
    private final Duration retryAfter;

    private RateLimitDecision(boolean allowed, RateLimitDimension dimension, Duration retryAfter) {
        this.allowed = allowed;
        this.dimension = dimension;
        this.retryAfter = retryAfter;
    }

    /** Returns the shared decision that lets the request through. */
    public static RateLimitDecision allowed() {
        return ALLOWED;
    }

    /** Creates a decision that refuses the request, naming the budget that tripped. */
    public static RateLimitDecision limited(RateLimitDimension dimension, Duration retryAfter) {
        if (dimension == null) {
            throw new IllegalArgumentException("dimension cannot be null");
        }
        if (retryAfter == null || retryAfter.isNegative()) {
            throw new IllegalArgumentException("retryAfter must be a non-negative duration");
        }
        return new RateLimitDecision(false, dimension, retryAfter);
    }

    public boolean isAllowed() {
        return allowed;
    }

    /**
     * Returns the budget that refused the request.
     *
     * @throws IllegalStateException if the decision is {@link #isAllowed() allowed}.
     */
    public RateLimitDimension getDimension() {
        requireLimited();
        return dimension;
    }

    /**
     * Returns how long the caller must wait before retrying.
     *
     * @throws IllegalStateException if the decision is {@link #isAllowed() allowed}.
     */
    public Duration getRetryAfter() {
        requireLimited();
        return retryAfter;
    }

    private void requireLimited() {
        if (allowed) {
            throw new IllegalStateException("an allowed decision has no dimension or retryAfter");
        }
    }
}
