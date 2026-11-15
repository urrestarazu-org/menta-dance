package com.menta.virtual.application.dto;

/**
 * Read model of one course the student is taking (#765). {@code completedLessons}, {@code
 * totalLessons}, {@code percentage} and the resume lesson have the same meaning as in {@link
 * CourseProgressView}; the titles are included so a client never resolves them against the catalog.
 */
public record InProgressCourseView(
    String courseId, String courseTitle, int completedLessons, int totalLessons, int percentage,
    ResumeLesson resumeLesson
) {

    /** The lesson to continue: the most recently played one of the course. */
    public record ResumeLesson(String lessonId, String moduleId, String lessonTitle, int positionSeconds) {
    }
}
