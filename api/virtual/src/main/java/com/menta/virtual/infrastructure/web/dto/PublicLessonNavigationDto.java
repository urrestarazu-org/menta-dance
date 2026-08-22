package com.menta.virtual.infrastructure.web.dto;

import com.menta.virtual.application.dto.PublicLessonNavigation;
import com.menta.virtual.application.dto.PublicLessonNavigationRef;

/**
 * Previous / next pointers on the public read. Either side may be
 * {@code null}; clients should render "no más lecciones" / "no hay
 * lección previa" prompts in that case rather than an empty card.
 */
public record PublicLessonNavigationDto(
    PublicLessonSummaryRefDto previousLesson,
    PublicLessonSummaryRefDto nextLesson
) {

    public static PublicLessonNavigationDto from(PublicLessonNavigation navigation) {
        return new PublicLessonNavigationDto(
            navigation.previousLesson() == null ? null : PublicLessonSummaryRefDto.from(navigation.previousLesson()),
            navigation.nextLesson() == null ? null : PublicLessonSummaryRefDto.from(navigation.nextLesson())
        );
    }
}
