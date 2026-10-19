package com.menta.physical.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * The span around a session's scheduled start during which check-in is possible
 * (US-PHYSICAL-001): {@code [scheduledAt - before, scheduledAt + after]}, both bounds inclusive.
 *
 * <p>Single home of that rule, shared by QR issuance, QR redemption and the student's own
 * session listing, so none of them can drift from the others.
 */
public record CheckInWindow(Duration before, Duration after) {

    public CheckInWindow {
        Objects.requireNonNull(before, "before cannot be null");
        Objects.requireNonNull(after, "after cannot be null");
        if (before.isNegative() || after.isNegative()) {
            throw new IllegalArgumentException("window bounds cannot be negative");
        }
    }

    /** Tells whether {@code now} falls inside the window of a session starting at {@code scheduledAt}. */
    public boolean isOpenAt(Instant scheduledAt, Instant now) {
        return !now.isBefore(scheduledAt.minus(before)) && !now.isAfter(scheduledAt.plus(after));
    }
}
