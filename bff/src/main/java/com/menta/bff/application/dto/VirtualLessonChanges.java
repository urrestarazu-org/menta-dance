package com.menta.bff.application.dto;

/**
 * The fields of a lesson to change: a {@code null} field is not sent and the API leaves it as it is. Once a video is
 * assigned there is no way to clear it.
 */
public record VirtualLessonChanges(
        String title, String description, String videoId, Integer durationMinutes, Boolean free, Integer order) {
}
