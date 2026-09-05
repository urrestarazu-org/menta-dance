package com.menta.virtual.infrastructure.web.dto;

import com.menta.virtual.application.dto.CourseProgressView;

/** Wire response for the course-progress aggregate endpoint (US-VIRTUAL-005, Slice 3). */
public record CourseProgressResponse(
    String courseId, int completedLessons, int totalLessons, int percentage, ResumeLesson resumeLesson
) {

    public record ResumeLesson(String lessonId, String moduleId, int positionSeconds, boolean completed) {

        private static ResumeLesson from(CourseProgressView.ResumeLesson resume) {
            return resume == null
                ? null
                : new ResumeLesson(resume.lessonId(), resume.moduleId(), resume.positionSeconds(), resume.completed());
        }
    }

    public static CourseProgressResponse from(CourseProgressView view) {
        return new CourseProgressResponse(
            view.courseId(), view.completedLessons(), view.totalLessons(), view.percentage(),
            ResumeLesson.from(view.resumeLesson())
        );
    }
}
