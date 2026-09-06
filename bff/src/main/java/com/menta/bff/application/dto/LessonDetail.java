package com.menta.bff.application.dto;

/**
 * Flattened wire shape combining the upstream lesson detail and its
 * navigation block, mirroring {@code PublicLessonFreeResponse}'s and
 * {@code PublicLessonPremiumAccessibleResponse}'s {@code lesson}+{@code
 * navigation} fields ({@code GET /api/v1/virtual/lessons/{lessonId}}).
 * The upstream {@code subscription}/{@code access} blocks are not
 * carried here — the BFF owns its own subscription CTA and the
 * granted/denied distinction is already structural (200 vs 403).
 * <p>
 * {@code videoId} is {@code null} on a free lesson — that only means the
 * upstream withholds the raw Bunny id, not that no stream exists for it;
 * see design's "A granted lesson always needs the stream call" note.
 * Component names match the JSON so no Jackson annotation is needed,
 * keeping this record framework-light per ADR-0021.
 * </p>
 *
 * @param lessonId    lesson identifier
 * @param title       lesson title
 * @param description lesson description
 * @param duration    formatted duration (e.g. {@code "05:00"})
 * @param order       display order within the module
 * @param videoId     raw video identifier, {@code null} for a free lesson
 * @param course      parent course reference
 * @param module      parent module reference
 * @param navigation  previous/next lesson pointers
 */
public record LessonDetail(
        String lessonId,
        String title,
        String description,
        String duration,
        int order,
        String videoId,
        CourseRef course,
        ModuleRef module,
        Nav navigation
) {

    /**
     * Compact parent-course reference.
     *
     * @param courseId course identifier
     * @param title    course title
     */
    public record CourseRef(String courseId, String title) {
    }

    /**
     * Compact parent-module reference.
     *
     * @param moduleId module identifier
     * @param title    module title
     */
    public record ModuleRef(String moduleId, String title) {
    }
}
