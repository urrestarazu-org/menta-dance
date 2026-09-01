package com.menta.billing.application.dto;

import java.time.Instant;

/**
 * A non-blocking warning that a new checkout's plan overlaps a still-in-force {@code CANCELLED}
 * subscription for that same plan (US-BILLING-011 D3).
 *
 * <p>The buyer's earlier subscription still grants access until {@code currentAccessEndsAt}; the
 * new purchase never blocks on this, extends it, or otherwise touches it — the notice is purely
 * informational.</p>
 */
public record OverlapNotice(String code, Instant currentAccessEndsAt) {

    public static final String OVERLAPPING_PAID_PERIOD = "OVERLAPPING_PAID_PERIOD";

    public static OverlapNotice of(Instant currentAccessEndsAt) {
        return new OverlapNotice(OVERLAPPING_PAID_PERIOD, currentAccessEndsAt);
    }
}
