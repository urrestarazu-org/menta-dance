package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.CourseDetail;

/**
 * Result of {@link GetCourseDetailViewUseCase}: the course-detail page's
 * rendering decision, made by the use case rather than the controller
 * (design decision D2).
 * <p>
 * A sealed interface makes the "no personalization without resumable
 * progress" guarantee structural instead of a template condition someone has
 * to remember (design decision D1): {@link Plain} has no {@link Resume}
 * field at all, so a denied, anonymous, or zero-progress visitor cannot carry
 * personalization even by accident. It also expresses atomically that
 * percentage, completed/total counts, and a resolved lesson title are either
 * all present ({@link Resumable}) or none of them are ({@link Plain}) — a
 * nullable field on a single combined record cannot say that. Java 21
 * pattern matching makes the controller's render switch exhaustive at
 * compile time, mirroring {@link LessonView}.
 * </p>
 */
public sealed interface CourseDetailView {

    /**
     * The catalog-only rendering: no percentage, no completed/total counts,
     * no "Continuar" link. Rendered for every case the spec collapses
     * together — anonymous visitors, a missing access token, a non-entitled
     * visitor (progress {@code 403}), zero progress or a zero-lesson course
     * (progress {@code resumeLesson == null}), any failure on the
     * supplementary progress call, and a resume {@code lessonId} that does
     * not resolve against the catalog projection.
     *
     * @param course the course detail fetched from the catalog call
     */
    record Plain(CourseDetail course) implements CourseDetailView {
    }

    /**
     * The personalized rendering for an authenticated, entitled visitor whose
     * course-progress aggregate reports a resumable lesson.
     *
     * @param course the course detail fetched from the catalog call
     * @param resume the resolved resume details
     */
    record Resumable(CourseDetail course, Resume resume) implements CourseDetailView {
    }

    /**
     * The "Continuar" call-to-action data, assembled from the course-progress
     * aggregate plus a title resolved by cross-referencing
     * {@code resumeLesson.lessonId} against the already-fetched
     * {@code CourseDetail.modules[].lessons[]} (the progress aggregate does
     * not carry a lesson title itself).
     * <p>
     * Deliberately carries no {@code positionSeconds} field, even though the
     * wire {@code CourseProgress.ResumeLesson} DTO has one: the "Continuar"
     * link is locked to {@code /courses/{courseId}/lessons/{lessonId}} only,
     * with no seek or position hint (locked product decision, design D1/D4)
     * — there is nothing for this record to carry that value to.
     * </p>
     *
     * @param lessonId         identifier of the lesson to resume
     * @param title            the resolved lesson title, from the course's module tree
     * @param percentage       completion percentage (0-100), as reported by the progress aggregate
     * @param completedLessons count of lessons the caller has completed
     * @param totalLessons     total lesson count for the course
     */
    record Resume(String lessonId, String title, int percentage, int completedLessons, int totalLessons) {
    }
}
