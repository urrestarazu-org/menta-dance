package com.menta.physical.application.dto;

/**
 * Cross-module read shape for an active recurring course — plain types only,
 * no domain value objects. Callers outside {@code api:physical} (namely
 * {@code api:app}'s catalog composition, #95) must never depend on this
 * module's internal domain model, only on this port-boundary contract.
 */
public record PhysicalCourseSummary(
    String courseId,
    String title,
    String professorName,
    String dayOfWeek,
    String startTime,
    String level,
    int capacity
) {
}
