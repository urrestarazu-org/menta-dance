package com.menta.virtual.infrastructure.web.dto;

import jakarta.validation.constraints.PositiveOrZero;

/**
 * Request body for editing a lesson; a {@code null} field is mapped to an empty {@code Optional}
 * and leaves the lesson value unchanged.
 */
public record UpdateVirtualLessonRequest(
    String title,
    String description,
    String videoId,
    @PositiveOrZero Integer durationMinutes,
    Boolean free,
    @PositiveOrZero Integer order
) {
}
