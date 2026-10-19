package com.menta.virtual.application.dto;

/**
 * Management view of a lesson returned by the lesson use cases; unlike the public projections it
 * includes the {@code videoId}.
 */
public record VirtualLessonManagementResult(
    String lessonId,
    String moduleId,
    String courseId,
    String title,
    String description,
    String videoId,
    int durationMinutes,
    boolean free,
    int order
) {
}
