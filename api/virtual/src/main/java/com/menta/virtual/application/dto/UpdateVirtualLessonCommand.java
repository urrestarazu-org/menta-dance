package com.menta.virtual.application.dto;

import java.util.Optional;

/** Partial lesson update: an empty {@link Optional} leaves the corresponding field unchanged. */
public record UpdateVirtualLessonCommand(
    Optional<String> title,
    Optional<String> description,
    Optional<String> videoId,
    Optional<Integer> durationMinutes,
    Optional<Boolean> free,
    Optional<Integer> order
) {
}
