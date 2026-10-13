package com.menta.bff.application.dto;

import java.time.Instant;

/**
 * A session the student holds a confirmed spot in, trimmed from the upstream
 * {@code GET /api/v1/physical/sessions/me} item to what the return page shows.
 *
 * @param sessionId   session identifier
 * @param courseName  name of the class, may be {@code null}
 * @param scheduledAt start of the session
 */
public record AssignedSession(String sessionId, String courseName, Instant scheduledAt) {
}
