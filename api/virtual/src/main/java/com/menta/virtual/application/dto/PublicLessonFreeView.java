package com.menta.virtual.application.dto;

/**
 * FREE branch — visitor may browse fully, no entitlement ever consulted.
 * Carries {@link PublicLessonNavigation} so the next / previous pointers
 * travel with the lesson, and {@link PublicLessonSubscriptionPrompt} for
 * the cross-sell CTA. The {@link PublicLessonDetailView} is built
 * without a {@code videoId}; the type variant {@link
 * com.menta.virtual.application.dto.PublicLessonDetailView#withoutVideoId}
 * enforces the invariant at compile time.
 */
public record PublicLessonFreeView(
    PublicLessonDetailView lesson,
    PublicLessonNavigation navigation,
    PublicLessonSubscriptionPrompt subscription
) implements PublicLessonView {
}
