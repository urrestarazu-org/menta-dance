package com.menta.virtual.infrastructure.web.dto;

import com.menta.virtual.application.dto.InProgressCourseView;

/** Wire response of one course of {@code GET /api/v1/virtual/courses/me/in-progress} (#765). */
public record InProgressCourseResponse(
    String courseId, String courseTitle, int completedLessons, int totalLessons, int percentage,
    ResumeLesson resumeLesson
) {

    /** The lesson to continue the course from. */
    public record ResumeLesson(String lessonId, String moduleId, String lessonTitle, int positionSeconds) {
    }

    /** Maps the application view to the wire response. */
    public static InProgressCourseResponse from(InProgressCourseView view) {
        InProgressCourseView.ResumeLesson resume = view.resumeLesson();
        return new InProgressCourseResponse(
            view.courseId(), view.courseTitle(), view.completedLessons(), view.totalLessons(), view.percentage(),
            new ResumeLesson(resume.lessonId(), resume.moduleId(), resume.lessonTitle(), resume.positionSeconds())
        );
    }
}
