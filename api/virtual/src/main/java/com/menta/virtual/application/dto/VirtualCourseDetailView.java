package com.menta.virtual.application.dto;

import java.util.List;

/**
 * Read-only, cross-module projection (#47) Virtual exposes for the public
 * course detail endpoint hosted by {@code api:app}. Composed once per
 * request from:
 * <ul>
 *   <li>the header fields of the {@code VirtualCourse} aggregate,</li>
 *   <li>{@code VirtualModule} rows fetched for that course ordered ascending
 *       by display order, each with its own {@code VirtualLesson} projection
 *       (id, title, duration in whole minutes, free flag, display order) and
 *       <strong>no {@code videoId}</strong> per US-VIRTUAL-002 escenario 1,</li>
 *   <li>the three pre-aggregated counts owned by the aggregate
 *       ({@code moduleCount}/{@code lessonCount}/{@code totalDurationMinutes})
 *       — never recomputed from the lesson walk.</li>
 * </ul>
 *
 * <p>The OMISSION of {@code instructor}/professor name is intentional: only
 * {@code professorId} is persisted on {@code VirtualCourse}, so the public
 * detail carries no instructor block rather than emitting an empty one
 * (#47 scope decision).</p>
 */
public record VirtualCourseDetailView(
    String courseId,
    String title,
    String description,
    String imageUrl,
    String category,
    String level,
    boolean isPremium,
    List<VirtualModuleDetail> modules,
    VirtualCourseStats stats
) {
}
