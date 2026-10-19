package com.menta.virtual.application.dto;

/**
 * PREMIUM and accessible branch — caller has an active entitlement to the
 * parent course, so the {@code videoId} surfaces for the future
 * streaming endpoint (US follow-up, out of scope for this PR). Navigation
 * is the same shape as the {@link PublicLessonFreeView} because the
 * lessons around a paid lesson in the same module may themselves be
 * free; the per-lesson {@code isFree} flag on the navigation ref lets the
 * client render the lock icon accordingly.
 */
public record PublicLessonPremiumAccessibleView(
    PublicLessonDetailView lesson,
    PublicLessonNavigation navigation
) implements PublicLessonView {
}
