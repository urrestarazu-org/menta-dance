package com.menta.virtual.application.dto;

import java.util.List;

/**
 * Cross-module module projection used by the published-course detail read
 * (#47). Carries the ordered lesson list (also projection-only) but never the
 * raw module entity — {@code api:app}'s catalog composition must stay
 * agnostic of {@code api:virtual}'s domain types past this port boundary.
 */
public record VirtualModuleDetail(
    String moduleId,
    String title,
    int order,
    List<VirtualLessonSummary> lessons
) {
}
