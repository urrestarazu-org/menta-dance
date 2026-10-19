package com.menta.virtual.application.dto;

import java.util.List;

/**
 * Cross-module module projection used by the admin course-detail read
 * (US-VIRTUAL-002 escenario 5, #125). Mirrors {@link VirtualModuleDetail}
 * structure but switches the lesson list's element type to
 * {@link VirtualLessonAdminSummary} (exposes {@code videoId}). Deliberately
 * separated from {@code VirtualModuleDetail} because the lesson type is
 * part of the contract: a future refactor cannot silently widen the lesson
 * type here without also updating the admin lessons.
 */
public record VirtualModuleAdminDetail(
    String moduleId,
    String title,
    int order,
    List<VirtualLessonAdminSummary> lessons
) {
}
