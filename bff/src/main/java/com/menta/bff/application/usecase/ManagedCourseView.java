package com.menta.bff.application.usecase;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * View model of a physical course in the management screens, with the recurrence in the academy's zone.
 *
 * @param courseId        course identifier
 * @param title           course name
 * @param description     course description
 * @param professorId     identifier of the instructor the course is assigned to
 * @param professorName   free-text name of the instructor
 * @param localDay        weekday in the academy's zone, to prefill the edit form
 * @param localTime       start time in the academy's zone, to prefill the edit form
 * @param scheduleLabel   Spanish label of both, e.g. {@code "Martes 22:00"}
 * @param durationMinutes length of each class
 * @param level           {@code BEGINNER}, {@code INTERMEDIATE} or {@code ADVANCED}
 * @param capacity        default number of spots of each session
 * @param active          whether the course is {@code ACTIVE}
 */
public record ManagedCourseView(
        String courseId, String title, String description, String professorId, String professorName,
        DayOfWeek localDay, LocalTime localTime, String scheduleLabel, int durationMinutes, String level,
        int capacity, boolean active) {
}
