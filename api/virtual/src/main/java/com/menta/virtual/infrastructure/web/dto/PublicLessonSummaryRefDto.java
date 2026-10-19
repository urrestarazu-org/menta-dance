package com.menta.virtual.infrastructure.web.dto;

import com.menta.virtual.application.dto.PublicLessonNavigationRef;

/**
 * Compact sibling reference used in {@link PublicLessonNavigationDto}.
 * Carries {@code isFree} so the client can render a lock badge without a
 * second round-trip on hover.
 */
public record PublicLessonSummaryRefDto(
    String lessonId,
    String title,
    boolean isFree
) {

    /** Maps the application sibling reference to its wire form. */
    public static PublicLessonSummaryRefDto from(PublicLessonNavigationRef ref) {
        return new PublicLessonSummaryRefDto(ref.lessonId(), ref.title(), ref.isFree());
    }
}
