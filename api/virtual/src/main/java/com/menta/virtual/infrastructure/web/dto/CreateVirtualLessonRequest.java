package com.menta.virtual.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * @param durationMinutes an integer, not the {@code "mm:ss"} display string
 *     from the original US-VIRTUAL-006 example — {@code
 *     virtual_lessons.duration_minutes} has been an {@code INT} since #46.
 * @param order {@code null} to append at the end of the module.
 */
public record CreateVirtualLessonRequest(
    @NotBlank String title,
    @NotBlank String description,
    @NotBlank String videoId,
    @PositiveOrZero int durationMinutes,
    boolean free,
    @PositiveOrZero Integer order
) {
}
