package com.menta.bff.application.dto;

import java.time.Instant;
import java.util.List;

/**
 * Public detail of a {@code PHYSICAL} course: the course data plus its
 * upcoming sessions with live availability, as published by the catalog
 * detail (#107).
 *
 * @param courseId      course identifier
 * @param title         course title
 * @param level         upstream level code
 * @param professorName teacher's display name
 * @param dayOfWeek     upstream weekday code
 * @param startTime     local start time, {@code HH:mm}
 * @param capacity      spots per session
 * @param sessions      upcoming sessions, ascending by start
 */
public record PhysicalClassDetail(
        String courseId,
        String title,
        String level,
        String professorName,
        String dayOfWeek,
        String startTime,
        int capacity,
        List<Session> sessions) {

    /**
     * One upcoming scheduled session.
     *
     * @param sessionId      session identifier
     * @param scheduledAt    start instant (UTC upstream)
     * @param capacity       spots in this session
     * @param availableSpots free spots right now, never negative
     */
    public record Session(String sessionId, Instant scheduledAt, int capacity, int availableSpots) {
    }
}
