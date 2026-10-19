package com.menta.virtual.application.dto;

/**
 * Subscription upsell prompt attached to the FREE view. Same purpose as
 * {@link PublicLessonAccessDto} but considered a positive
 * "you-can-upgrade" hint rather than a hard "not-allowed" signal; the
 * {@code allowed} flag is intentionally absent here so the type tells
 * you "this lesson is accessible, here is a marketing CTA".
 */
public record PublicLessonSubscriptionPrompt(
    String message,
    String plansUrl
) {
}
