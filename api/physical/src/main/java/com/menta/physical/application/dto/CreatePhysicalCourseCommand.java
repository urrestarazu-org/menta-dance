package com.menta.physical.application.dto;

import com.menta.physical.domain.model.PhysicalCourseLevel;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

/**
 * @param professorId {@code null} means "default to the authenticated
 *     instructor" — only meaningful when the actor is ADMIN; an INSTRUCTOR
 *     supplying a non-null, non-self id is rejected (see {@code
 *     ProfessorMismatchException}).
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
