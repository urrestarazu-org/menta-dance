package com.menta.bff.application.dto;

/** A lesson as the management API reports it after a write ({@code VirtualLessonManagementResponse}). */
public record ManagedVirtualLesson(
        String lessonId, String moduleId, String courseId, String title, String description, String videoId,
        int durationMinutes, boolean free, int order) {
}
