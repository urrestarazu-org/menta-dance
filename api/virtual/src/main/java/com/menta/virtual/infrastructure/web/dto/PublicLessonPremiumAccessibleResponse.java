package com.menta.virtual.infrastructure.web.dto;

import com.menta.virtual.application.dto.PublicLessonPremiumAccessibleView;

/** Premium accessible response — {@code lesson} DOES carry {@code videoId}. */
public record PublicLessonPremiumAccessibleResponse(
    PublicLessonDetailDto lesson,
    PublicLessonNavigationDto navigation
) implements PublicLessonResponse {

    public static PublicLessonPremiumAccessibleResponse from(PublicLessonPremiumAccessibleView view) {
        return new PublicLessonPremiumAccessibleResponse(
            PublicLessonDetailDto.fromPremium(view.lesson()),
            PublicLessonNavigationDto.from(view.navigation())
        );
    }
}
