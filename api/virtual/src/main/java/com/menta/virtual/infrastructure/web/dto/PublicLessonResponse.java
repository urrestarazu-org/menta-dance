package com.menta.virtual.infrastructure.web.dto;

/**
 * Sealed parent for the response body of
 * {@code GET /api/v1/virtual/lessons/{lessonId}}. Mirrors the
 * application-sealed {@link com.menta.virtual.application.dto.PublicLessonView}
 * tree, with a JSON layout chosen for the BFF / Android clients rather
 * than the domain: each variant carries the shape the orchestrator
 * spec'd for the matching scenario.
 */
public sealed interface PublicLessonResponse
    permits PublicLessonFreeResponse,
            PublicLessonPremiumAccessibleResponse,
            PublicLessonRequiresSubscriptionResponse {
}
