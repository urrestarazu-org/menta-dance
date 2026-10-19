package com.menta.virtual.application.dto;

/**
 * Cross-module lesson projection used by the published-course detail read
 * (#47, {@code VirtualCourseCatalogPort.findPublishedDetailById}). Deliberately
 * omits {@code videoId}: a visitor requesting the public course detail must
 * never receive the Bunny.net opaque reference for premium lessons
 * (US-VIRTUAL-002 escenario 1 BDD rule). The domain model carries
 * {@code videoId}; this projection is built only after the port decides this
 * caller is the public detail endpoint, never the management endpoints.
 *
 * <p>{@code durationMinutes} is kept as the integer-minute domain value here —
 * formatting to {@code "mm:ss"} / {@code "h:mm"} is the responsibility of the
 * presentation layer in {@code api:app}, not the implementation layer. See
 * {@code CatalogCourseDetailResponse} for the formatter. This split keeps the
 * domain projection free of UI conventions.</p>
 */
public record VirtualLessonSummary(
    String lessonId,
    String title,
    int durationMinutes,
    boolean isFree,
    int order
) {
}
