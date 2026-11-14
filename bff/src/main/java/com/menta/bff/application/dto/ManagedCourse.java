package com.menta.bff.application.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * A physical course as the management API reports it ({@code PhysicalCourseManagementResponse}). The recurrence
 * is in UTC, as the API stores it (ADR-0048); the use cases convert it to the academy's zone for display.
 *
 * @param courseId        course identifier
 * @param title           course name
 * @param description     course description
 * @param professorId     identifier of the instructor the course is assigned to
 * @param professorName   free-text name of the instructor
 * @param dayOfWeek       weekday of the recurrence, in UTC
 * @param startTime       start time of each class, in UTC
 * @param durationMinutes length of each class
 * @param level           {@code BEGINNER}, {@code INTERMEDIATE} or {@code ADVANCED}
 * @param capacity        default number of spots of each session
 * @param status          {@code ACTIVE} or {@code INACTIVE}
 */
public record ManagedCourse(
        String courseId, String title, String description, String professorId, String professorName,
        DayOfWeek dayOfWeek, LocalTime startTime, int durationMinutes, String level, int capacity,
        String status) {
}
