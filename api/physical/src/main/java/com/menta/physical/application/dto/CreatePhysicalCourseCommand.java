package com.menta.physical.application.dto;

import com.menta.physical.domain.model.PhysicalCourseLevel;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Application-layer command to create a recurring physical course.
 *
 * @param professorId {@code null} means "default to the authenticated
 *     instructor" and is only accepted when the actor is an INSTRUCTOR; an
 *     ADMIN omitting it is rejected (see {@code ProfessorRequiredException})
 *     and an INSTRUCTOR supplying a non-null, non-self id is rejected (see
 *     {@code ProfessorMismatchException}).
 */
public record CreatePhysicalCourseCommand(
    String title,
    String description,
    UUID professorId,
    String professorName,
    DayOfWeek dayOfWeek,
    LocalTime startTime,
    int durationMinutes,
    PhysicalCourseLevel level,
    int capacity
) {
}
