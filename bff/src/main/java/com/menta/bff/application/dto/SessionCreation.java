package com.menta.bff.application.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Body of {@code POST /api/v1/admin/physical/courses/{courseId}/sessions}, with the date and time already in UTC.
 *
 * @param date      UTC date of the session
 * @param startTime UTC start time
 * @param capacity  spots of the session, or {@code null} to inherit the course's
 * @param notes     free-text notes, or {@code null}
 */
public record SessionCreation(LocalDate date, LocalTime startTime, Integer capacity, String notes) {
}
