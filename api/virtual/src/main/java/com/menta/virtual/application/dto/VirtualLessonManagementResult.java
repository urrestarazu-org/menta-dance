package com.menta.virtual.application.dto;

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
