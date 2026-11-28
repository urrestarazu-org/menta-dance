package com.menta.bff.application.dto;

/**
 * A lesson that uses a video, with its course and module ({@code VirtualVideoLesson} in {@code virtual-v1.yaml}).
 * The identifiers are what the edit screen of the lesson needs; the titles are what a person reads.
 */
public record VideoLesson(
        String courseId, String courseTitle, String moduleId, String moduleTitle, String lessonId,
        String lessonTitle) {
}
