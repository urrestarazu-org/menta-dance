package com.menta.virtual.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Request body for creating a lesson; the title and description must not be blank and the numeric
 * fields must not be negative.
 *
 * @param durationMinutes an integer, not the {@code "mm:ss"} display string
 *     from the original US-VIRTUAL-006 example — {@code
 *     virtual_lessons.duration_minutes} has been an {@code INT} since #46.
 * @param videoId {@code null} for a lesson created as a stub with no video
 *     assigned yet — {@code VirtualLesson.isComplete()} depends on this
 *     being reachable; a lesson can never be published without one, but a
 *     draft lesson can exist without one while content is being prepared.
 * @param order {@code null} to append at the end of the module.
 */
public record CreateVirtualLessonRequest(
    @NotBlank String title,
    @NotBlank String description,
    String videoId,
    @PositiveOrZero int durationMinutes,
    boolean free,
    @PositiveOrZero Integer order
) {
}
