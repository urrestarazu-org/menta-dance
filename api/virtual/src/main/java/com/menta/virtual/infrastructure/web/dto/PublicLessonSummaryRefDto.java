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

    public static PublicLessonSummaryRefDto from(PublicLessonNavigationRef ref) {
        return new PublicLessonSummaryRefDto(ref.lessonId(), ref.title(), ref.isFree());
    }
}
