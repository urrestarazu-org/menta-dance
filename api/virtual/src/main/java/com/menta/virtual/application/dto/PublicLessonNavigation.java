package com.menta.virtual.application.dto;

/**
 * Previous / next navigation around a lesson, returned in
 * {@link PublicLessonFreeView} and {@link PublicLessonPremiumAccessibleView}.
 * Either pointer is {@code null} when the lesson is the first / last in
 * its module — the controller renders "no more lessons" prompts against
 * {@code null}.
 */
public record PublicLessonNavigation(
    PublicLessonNavigationRef previousLesson,
    PublicLessonNavigationRef nextLesson
) {
}
