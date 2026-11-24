package com.menta.virtual.application.dto;

/** A lesson that uses a video, with the course and module it belongs to; ids are rendered as strings. */
public record VideoLessonUsage(
    String courseId,
    String courseTitle,
    String moduleId,
    String moduleTitle,
    String lessonId,
    String lessonTitle
) {
}
