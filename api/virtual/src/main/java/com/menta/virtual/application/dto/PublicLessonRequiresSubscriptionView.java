package com.menta.virtual.application.dto;

/**
 * Premium but NOT currently subscribed — caller is authenticated and
 * has therefore earned a richer preview than an anonymous visitor would
 * (still HTTP 200, NOT 403: the orchestrator's decision is that anonymous
 * premium → 403, but an identified-but-not-subscribed caller receives
 * the teaser with an explicit {@code access.allowed=false} flag).
 * Drops {@code videoId} by construction: the {@link
 * PublicLessonPreviewView} type cannot carry it.
 */
public record PublicLessonRequiresSubscriptionView(
    PublicLessonPreviewView lesson,
    LessonAccessDecisionDto access
) implements PublicLessonView {
}
