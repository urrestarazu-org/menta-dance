package com.menta.bff.application.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * What the management form submits to create a course. The weekday and time are the ones in the academy's zone;
 * the use case converts them to UTC (ADR-0048).
 *
 * @param title           course name
 * @param description     course description
 * @param professorId     instructor the course is assigned to: the administrator's choice, or the caller's own id
 *                        for an instructor
 * @param professorName   free-text name of the instructor
 * @param localDay        weekday in the academy's zone
 * @param localTime       start time in the academy's zone
 * @param durationMinutes length of each class
 * @param level           {@code BEGINNER}, {@code INTERMEDIATE} or {@code ADVANCED}
 * @param capacity        default number of spots of each session
 */
public record CreateCourseCommand(
        String title, String description, String professorId, String professorName, DayOfWeek localDay,
        LocalTime localTime, int durationMinutes, String level, int capacity) {
}
