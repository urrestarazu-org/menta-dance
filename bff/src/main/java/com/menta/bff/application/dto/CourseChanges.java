package com.menta.bff.application.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Body of {@code PATCH /api/v1/admin/physical/courses/{courseId}}: only the fields that are not {@code null}
 * are sent. The recurrence is in UTC; the instructor cannot be changed.
 *
 * @param title           new name
 * @param description     new description
 * @param dayOfWeek       new weekday, in UTC
 * @param startTime       new start time, in UTC
 * @param durationMinutes new length of each class
 * @param level           new level
 * @param capacity        new default number of spots
 * @param status          {@code ACTIVE} or {@code INACTIVE}
 */
public record CourseChanges(
        String title, String description, DayOfWeek dayOfWeek, LocalTime startTime, Integer durationMinutes,
        String level, Integer capacity, String status) {
}
