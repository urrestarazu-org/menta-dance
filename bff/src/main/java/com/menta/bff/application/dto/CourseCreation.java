package com.menta.bff.application.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Body of {@code POST /api/v1/admin/physical/courses}, with the recurrence already in UTC. The BFF always sends
 * an explicit {@code professorId}, never relying on the API's default.
 *
 * @param title           course name
 * @param description     course description
 * @param professorId     identifier of the instructor the course is assigned to
 * @param professorName   free-text name of the instructor
 * @param dayOfWeek       weekday of the recurrence, in UTC
 * @param startTime       start time of each class, in UTC
 * @param durationMinutes length of each class, at least 1
 * @param level           {@code BEGINNER}, {@code INTERMEDIATE} or {@code ADVANCED}
 * @param capacity        default number of spots of each session, at least 1
 */
public record CourseCreation(
        String title, String description, String professorId, String professorName, DayOfWeek dayOfWeek,
        LocalTime startTime, int durationMinutes, String level, int capacity) {
}
