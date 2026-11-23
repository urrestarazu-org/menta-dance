package com.menta.virtual.infrastructure.web.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Request body for editing a lesson; a {@code null} field is mapped to an empty {@code Optional}
 * and leaves the lesson value unchanged.
 *
 * @param videoId {@code null} (or omitted) keeps the current video; a blank value (empty or only
 *     whitespace) is rejected with {@code 400} instead of clearing it (#845): a video cannot be
 *     removed once assigned.
 */
public record UpdateVirtualLessonRequest(
    String title,
    String description,
    @Pattern(regexp = "(?s).*\\S.*") String videoId,
    @PositiveOrZero Integer durationMinutes,
    Boolean free,
    @PositiveOrZero Integer order
) {
}
