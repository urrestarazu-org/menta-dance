package com.menta.physical.infrastructure.web.dto;

import com.menta.physical.domain.model.PhysicalCourseLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * {@code professorName} is not part of the original US-PHYSICAL-005 contract
 * — added as a deliberate, documented deviation. No port exists yet to
 * resolve a display name for an {@code api:auth} user id from Physical
 * ({@code UserQueryPort} is only a documentation example in
 * docs/27-CLEAN-ARCHITECTURE-GUIDE.md, never implemented), and building a
 * cross-module port solely for a display string on an admin screen would be
 * over-engineering. {@code professorId} is null for an INSTRUCTOR creating
 * their own course — it is resolved to the caller's own id; a non-null value
 * that doesn't match is rejected (see {@code ProfessorMismatchException}).
 */
public record CreatePhysicalCourseRequest(
    @NotBlank String title,
    @NotBlank String description,
    String professorId,
    @NotBlank String professorName,
    @NotNull DayOfWeek dayOfWeek,
    @NotNull LocalTime startTime,
    @Positive int durationMinutes,
    @NotNull PhysicalCourseLevel level,
    @Positive int capacity
) {
}
