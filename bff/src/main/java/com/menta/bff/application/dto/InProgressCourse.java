package com.menta.bff.application.dto;

/**
 * A course the student is in the middle of, as {@code GET /api/v1/virtual/courses/me/in-progress}
 * reports it (#363), trimmed to what the dashboard renders. The lesson's module and playback
 * position are left out: the lesson page resolves both itself.
 *
 * @param courseId          course identifier
 * @param title             course title
 * @param completedLessons  lessons the student has completed
 * @param totalLessons      lessons in the course
 * @param percentage        completion percentage (0-99: a finished course is never listed)
 * @param resumeLessonId    lesson to continue the course from
 * @param resumeLessonTitle title of that lesson
 */
public record InProgressCourse(
        String courseId,
        String title,
        int completedLessons,
        int totalLessons,
        int percentage,
        String resumeLessonId,
        String resumeLessonTitle
) {
}
