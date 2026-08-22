package com.menta.virtual.application.dto;

import java.util.List;

/**
 * Read-only, cross-module projection Virtual exposes for the admin
 * course-detail read (US-VIRTUAL-002 escenario 5, #125). Mirrors
 * {@link VirtualCourseDetailView} — the public detail projection — but:
 * <ul>
 *   <li>adds {@code status} (a {@code CourseStatus} name string — one of
 *       {@code DRAFT}/{@code PUBLISHED}/{@code ARCHIVED}) so the admin UI
 *       can render a "visible to visitors?" badge and an
 *       unpublish/publish affordance,</li>
 *   <li>uses {@link VirtualModuleAdminDetail} for every module (whose
 *       lessons are typed as {@link VirtualLessonAdminSummary}, exposing
 *       {@code videoId}) — the Bunny.net reference is intentionally visible
 *       to authenticated operators,</li>
 *   <li>still carries the same pre-aggregated counts
 *       ({@code moduleCount}/{@code lessonCount}/{@code totalDurationMinutes})
 *       so the admin view can show how much content belongs to a course in
 *       any state without recomputing from the lesson walk,</li>
 *   <li>does NOT carry {@code shortDescription}/{@code professorId} — those
 *       are owned by the management summary contract (US-VIRTUAL-006) and
 *       the public catalog uses {@code shortDescription} only at the list
 *       level (#95); the rich detail view for both audiences deliberately
 *       skips them (see {@link VirtualCourseDetailView} for the public
 *       rationale).</li>
 * </ul>
 *
 * <p>Unlike the public path
 * ({@code VirtualCourseCatalogPort.findPublishedDetailById}), this
 * projection exposes {@code status} because the operator needs to act on
 * it — and unlike the same public path, this projection does NOT merge
 * "not found" with "exists but not published": for the admin port
 * {@code Optional.empty()} means only "does not exist"; {@code DRAFT} and
 * {@code ARCHIVED} courses are full, status-bearing views.</p>
 *
 * <p>{@code status} is declared as its domain enum (not flatted to a
 * string) here so the {@code findByIdForAdmin} implementation keeps the
 * type close to the source — callers that want a stable wire representation
 * (HTTP, JSON) should defer the flattening to the implementation layer's
 * response factory, mirroring how {@link VirtualCourseDetailView} also keeps
 * {@code category}/{@code level} as raw source values.</p>
 */
public record VirtualCourseAdminDetailView(
    String courseId,
    String title,
    String description,
    String imageUrl,
    String category,
    String level,
    boolean isPremium,
    com.menta.virtual.domain.model.CourseStatus status,
    List<VirtualModuleAdminDetail> modules,
    VirtualCourseStats stats
) {
}
