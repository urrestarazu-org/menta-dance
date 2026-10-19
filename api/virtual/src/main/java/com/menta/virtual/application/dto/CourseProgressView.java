package com.menta.virtual.application.dto;

/**
 * Read model for the course-progress aggregate (US-VIRTUAL-005, Slice 3). {@code resumeLesson} is
 * {@code null} on an empty (zero-lesson) course — never an empty/not-found signal, since the
 * course itself was already resolved before this view is built.
 */
public record CourseProgressView(
    String courseId, int completedLessons, int totalLessons, int percentage, ResumeLesson resumeLesson
) {

    public record ResumeLesson(String lessonId, String moduleId, int positionSeconds, boolean completed) {
    }
}
