package com.menta.physical.application.dto;

import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.domain.model.PhysicalCourseLevel;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Optional;

/**
 * Partial update — {@link Optional#empty()} means "field not present in the
 * PATCH body, leave unchanged", distinct from a present-but-null JSON value
 * (rejected at the web layer, never reaches this command).
 */
public record UpdatePhysicalCourseCommand(
    Optional<String> title,
    Optional<String> description,
    Optional<DayOfWeek> dayOfWeek,
    Optional<LocalTime> startTime,
    Optional<Integer> durationMinutes,
    Optional<PhysicalCourseLevel> level,
    Optional<Integer> capacity,
    Optional<CourseStatus> status
) {
}
