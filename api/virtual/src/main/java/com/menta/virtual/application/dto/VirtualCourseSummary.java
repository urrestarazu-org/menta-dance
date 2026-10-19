package com.menta.virtual.application.dto;

/**
 * Cross-module read shape for a published course — plain types only, no
 * domain value objects. Callers outside {@code api:virtual} (namely
 * {@code api:app}'s catalog composition, #95) must never depend on this
 * module's internal domain model, only on this port-boundary contract.
 */
public record VirtualCourseSummary(
    String courseId,
    String title,
    String shortDescription,
    String imageUrl,
    String category,
    String level,
    boolean premium,
    int moduleCount,
    int lessonCount,
    int totalDurationMinutes
) {
}
