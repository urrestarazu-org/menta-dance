package com.menta.virtual.application.dto;

/**
 * Cross-module aggregate statistics used by the published-course detail read
 * (#47). Mirrors the three pre-aggregated counts on the {@code api:virtual}
 * course aggregate (see {@code VirtualCourse.getModuleCount()} etc.) — the
 * port implementation must NEVER recompute them by walking modules and
 * lessons, both to keep this read cheap and to avoid any divergence between
 * the detail view's stats and the same course's summary stats surfaced by
 * {@code findPublishedById}.
 *
 * <p>{@code totalDurationMinutes} is plain minutes; presentation formatting
 * is the caller's responsibility.</p>
 */
public record VirtualCourseStats(
    int moduleCount,
    int lessonCount,
    int totalDurationMinutes
) {
}
