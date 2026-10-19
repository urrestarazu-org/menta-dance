package com.menta.bff.application.dto;

import java.time.Instant;
import java.util.List;

/**
 * A physical course as shown on its public page, trimmed from the upstream
 * {@code CatalogPhysicalCourseDetail} to what the page renders.
 *
 * @param courseId      course identifier
 * @param title         course title
 * @param level         upstream level code (e.g. {@code ADVANCED}), may be {@code null}
 * @param professorName professor name
 * @param dayOfWeek     upstream weekday code (e.g. {@code MONDAY})
 * @param startTime     start time as {@code HH:mm}
 * @param capacity      course capacity
 * @param sessions      upcoming sessions, ascending by time, never {@code null}
 */
public record PhysicalCourseDetail(
        String courseId,
        String title,
        String level,
        String professorName,
        String dayOfWeek,
        String startTime,
        int capacity,
        List<Session> sessions) {

    /**
     * One upcoming session.
     *
     * @param sessionId      session identifier
     * @param scheduledAt    start instant (UTC)
     * @param capacity       session capacity
     * @param availableSpots free spots, 0 when the session is full
     */
    public record Session(String sessionId, Instant scheduledAt, int capacity, int availableSpots) {
    }
}
