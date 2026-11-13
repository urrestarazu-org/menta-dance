package com.menta.bff.application.dto;

import java.time.Instant;

/**
 * A session of the staff's agenda, trimmed from the upstream
 * {@code GET /api/v1/admin/physical/sessions?from=&to=} item to what the reception screen shows.
 *
 * @param sessionId   session identifier
 * @param courseName  name of the class
 * @param scheduledAt start of the session
 * @param cancelled   whether the session is cancelled
 * @param capacity    total spots of the session
 * @param occupied    spots that count against the capacity: confirmed assignments plus live holds
 */
public record StaffSession(
        String sessionId, String courseName, Instant scheduledAt, boolean cancelled, int capacity, int occupied) {
}
