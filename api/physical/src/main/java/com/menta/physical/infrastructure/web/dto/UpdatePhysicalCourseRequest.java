package com.menta.physical.infrastructure.web.dto;

import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.domain.model.PhysicalCourseLevel;
import jakarta.validation.constraints.Positive;
import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Partial update — every field is nullable and {@code null} means "not
 * present in this PATCH, leave unchanged". {@code @Positive} is null-safe by
 * Jakarta Bean Validation convention (only fires when a client actually
 * sends a value) — {@code @NotBlank} is NOT null-safe (it requires non-null
 * by definition), so {@code title}/{@code description} are deliberately
 * unvalidated here: a blank value is caught downstream by {@code
 * PhysicalCourse}'s own constructor only if it were null, which Jackson
 * never produces for an absent JSON field.
 */
public record UpdatePhysicalCourseRequest(
    String title,
    String description,
    DayOfWeek dayOfWeek,
    LocalTime startTime,
    @Positive Integer durationMinutes,
    PhysicalCourseLevel level,
    @Positive Integer capacity,
    CourseStatus status
) {
}
