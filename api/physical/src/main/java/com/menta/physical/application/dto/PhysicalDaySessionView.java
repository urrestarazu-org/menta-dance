package com.menta.physical.application.dto;

import com.menta.physical.domain.model.SessionStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * One session of a staff day listing (#355).
 *
 * <p>{@code endsAt} is {@code scheduledAt} plus the course duration. {@code occupied} is the
 * confirmed assignments plus the unexpired, unconverted holds, i.e. what the session's availability
 * subtracts from {@code capacity}; it is not floored, so an oversold session shows more than its
 * capacity.
 */
public record PhysicalDaySessionView(
    UUID sessionId,
    UUID courseId,
    String courseName,
    Instant scheduledAt,
    Instant endsAt,
    SessionStatus status,
    int capacity,
    int occupied
) {
}
