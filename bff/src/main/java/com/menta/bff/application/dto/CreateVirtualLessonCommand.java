package com.menta.bff.application.dto;

/**
 * What the new-lesson form submits. A {@code null} {@code videoId}, {@code durationMinutes}, {@code free} or
 * {@code order} is not sent: the API creates the lesson without video, with duration 0, not free, and appends it at
 * the end of the module.
 */
public record CreateVirtualLessonCommand(
        String title, String description, String videoId, Integer durationMinutes, Boolean free, Integer order) {
}
