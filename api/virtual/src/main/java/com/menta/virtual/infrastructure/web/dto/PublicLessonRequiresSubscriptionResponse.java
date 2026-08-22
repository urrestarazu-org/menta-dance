package com.menta.virtual.infrastructure.web.dto;

import com.menta.virtual.application.dto.PublicLessonRequiresSubscriptionView;

/**
 * Authenticated-but-no-entitlement response. Still HTTP 200 — the
 * orchestrator's spec (#48): anonymous → 403 ProblemDetail, identified
 * but not subscribed → 200 with explicit
 * {@code access.allowed=false} flag so the client can decide whether
 * to push an upgrade modal.
 */
public record PublicLessonRequiresSubscriptionResponse(
    PublicLessonPreviewDto lesson,
    PublicLessonAccessDto access
) implements PublicLessonResponse {

    public static PublicLessonRequiresSubscriptionResponse from(PublicLessonRequiresSubscriptionView view) {
        return new PublicLessonRequiresSubscriptionResponse(
            PublicLessonPreviewDto.from(view.lesson()),
            PublicLessonAccessDto.from(view.access())
        );
    }
}
