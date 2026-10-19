package com.menta.bff.application.dto;

/**
 * Wire shape of {@code GET /api/v1/virtual/courses/{courseId}/progress},
 * mirroring {@code api:virtual}'s {@code CourseProgressResponse} byte-for-byte
 * (component names match the JSON so no Jackson annotation is needed, keeping
 * this record framework-light per ADR-0021).
 *
 * @param courseId          course identifier
 * @param completedLessons  count of lessons the caller has completed
 * @param totalLessons      total lesson count for the course
 * @param percentage        completion percentage (0-100)
 * @param resumeLesson      the lesson to resume, or {@code null} when there is
 *                          no in-progress lesson (zero-lesson course or zero progress)
 */
public record CourseProgress(
        String courseId,
        int completedLessons,
        int totalLessons,
        int percentage,
        ResumeLesson resumeLesson
) {

    /**
     * The lesson the caller should resume, with its playback position.
     *
     * @param lessonId       lesson identifier
     * @param moduleId       module identifier the lesson belongs to
     * @param positionSeconds last known playback position in seconds
     * @param completed      whether this lesson has already been completed
     */
    public record ResumeLesson(
            String lessonId,
            String moduleId,
            int positionSeconds,
            boolean completed
    ) {
    }
}
