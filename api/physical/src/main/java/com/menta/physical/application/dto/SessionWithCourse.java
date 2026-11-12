package com.menta.physical.application.dto;

import com.menta.physical.domain.model.PhysicalSession;

/**
 * A session together with the course data staff listings need, resolved in one read so callers
 * never query a course per session.
 *
 * <p>{@code session} carries the live assigned and held counts; {@code courseDurationMinutes} is
 * the course's current duration, from which a session's end is derived.
 */
public record SessionWithCourse(PhysicalSession session, String courseTitle, int courseDurationMinutes) {
}
