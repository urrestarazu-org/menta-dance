package com.menta.virtual.application.dto;

/**
 * Cross-module lesson projection used by the admin course-detail read
 * (US-VIRTUAL-002 escenario 5, #125). Differentiated from
 * {@link VirtualLessonSummary} — the public/detail read sees the same lesson
 * WITHOUT {@code videoId}; this admin variant exposes it explicitly because
 * the admin UI needs the Bunny.net reference to render preview/embed.
 *
 * <p>The distinction is what stops Bunny.net references from leaking to
 * visitors (US-VIRTUAL-002 escenario 1 BDD rule): every admin path chooses
 * {@code VirtualLessonAdminSummary}, every public path chooses
 * {@code VirtualLessonSummary}, and the domain model carries {@code videoId}
 * itself — making it possible to forget the difference in a future port
 * method. The {@code VirtualCourseCatalogPortImplTest}-style "static record
 * components" assertion that ships alongside this type guards that
 * invariant at compile/test time.</p>
 *
 * <p>{@code durationMinutes} stays as the integer-minute domain value here —
 * formatting to {@code "mm:ss"}/{@code "Xm"} is the implementation layer's
 * responsibility (see {@code VirtualCourseAdminDetailResponse.Formats}).</p>
 */
public record VirtualLessonAdminSummary(
    String lessonId,
    String title,
    int durationMinutes,
    boolean isFree,
    int order,
    String videoId
) {
}
