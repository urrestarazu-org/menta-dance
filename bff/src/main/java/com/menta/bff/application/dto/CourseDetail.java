package com.menta.bff.application.dto;

import java.util.List;

/**
 * Wire shape of {@code GET /api/v1/catalog/courses/{courseId}}, mirroring
 * {@code CatalogCourseDetailResponse} byte-for-byte (component names match
 * the JSON so no Jackson annotation is needed, keeping this record
 * framework-light per ADR-0021).
 *
 * @param courseId    course identifier
 * @param title       course title
 * @param description course description
 * @param thumbnailUrl course thumbnail image URL
 * @param category    course category
 * @param level       course difficulty level
 * @param isPremium   whether the course requires an active subscription
 * @param modules     ordered modules with their lessons
 * @param stats       aggregate module/lesson/duration counters
 */
public record CourseDetail(
        String courseId,
        String title,
        String description,
        String thumbnailUrl,
        String category,
        String level,
        boolean isPremium,
        List<Module> modules,
        Stats stats
) {

    /**
     * A course module with its ordered lessons.
     *
     * @param moduleId module identifier
     * @param title    module title
     * @param order    display order within the course
     * @param lessons  ordered lessons within the module
     */
    public record Module(
            String moduleId,
            String title,
            int order,
            List<Lesson> lessons
    ) {
    }

    /**
     * A lesson summary as rendered in the course-detail page.
     *
     * @param lessonId lesson identifier
     * @param title    lesson title
     * @param duration formatted duration (e.g. {@code "05:00"})
     * @param isFree   whether the lesson is free to preview
     * @param order    display order within the module
     */
    public record Lesson(
            String lessonId,
            String title,
            String duration,
            boolean isFree,
            int order
    ) {
    }

    /**
     * Aggregate course statistics.
     *
     * @param moduleCount   total module count
     * @param lessonCount   total lesson count
     * @param totalDuration formatted total duration (e.g. {@code "2h 30m"})
     */
    public record Stats(
            int moduleCount,
            int lessonCount,
            String totalDuration
    ) {
    }
}
