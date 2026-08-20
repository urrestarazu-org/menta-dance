package com.menta.virtual.application.dto;

import java.util.Optional;

/**
 * @param durationMinutes an integer, not the {@code "mm:ss"} string shown in
 *     the original US-VIRTUAL-006 example — {@code virtual_lessons.duration_minutes}
 *     has been an {@code INT} since #46, and parsing a display format into
 *     it would be needless complexity for an admin form.
 * @param order empty means "append at the end of the module".
 */
public record CreateVirtualLessonCommand(
    String title, String description, String videoId, int durationMinutes, boolean free, Optional<Integer> order
) {
}
