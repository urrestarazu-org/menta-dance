package com.menta.bff.application.dto;

/**
 * Lesson summary derived from a {@link CourseDetail.Lesson} entry, used to
 * render {@code LessonView.Sample} when the upstream lesson endpoint denies
 * access (bare 403, no body to parse). Field shape mirrors
 * {@link CourseDetail.Lesson} exactly, since it is built from that same
 * course-detail projection already fetched for the request.
 *
 * @param lessonId lesson identifier
 * @param title    lesson title
 * @param duration formatted duration (e.g. {@code "05:00"})
 * @param isFree   whether the lesson is free to preview
 * @param order    display order within the module
 */
public record LessonSummary(
        String lessonId,
        String title,
        String duration,
        boolean isFree,
        int order
) {
}
