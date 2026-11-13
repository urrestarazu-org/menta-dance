package com.menta.bff.application.dto;

import java.time.Instant;
import java.util.List;

/**
 * Roster of a session, from {@code GET /api/v1/admin/physical/sessions/{sessionId}/roster}.
 *
 * @param sessionId   session identifier
 * @param courseName  name of the class
 * @param scheduledAt start of the session
 * @param cancelled   whether the session is cancelled
 * @param capacity    total spots of the session
 * @param students    students with a confirmed spot, in upstream order
 */
public record SessionRoster(
        String sessionId, String courseName, Instant scheduledAt, boolean cancelled, int capacity,
        List<RosterStudent> students) {

    public SessionRoster {
        students = List.copyOf(students);
    }
}
