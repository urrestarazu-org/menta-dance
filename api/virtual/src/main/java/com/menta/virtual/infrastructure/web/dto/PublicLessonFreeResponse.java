package com.menta.virtual.infrastructure.web.dto;

import com.menta.virtual.application.dto.PublicLessonFreeView;

/** Free lesson response — {@code lesson} carries every field except {@code videoId}. */
public record PublicLessonFreeResponse(
    PublicLessonDetailDto lesson,
    PublicLessonNavigationDto navigation,
    PublicLessonSubscriptionPromptDto subscription,
    PublicLessonAccessMetadataDto access
) implements PublicLessonResponse {

    /** Maps the free-lesson view, marking the access metadata as a public preview. */
    public static PublicLessonFreeResponse from(PublicLessonFreeView view) {
        return new PublicLessonFreeResponse(
            PublicLessonDetailDto.fromFree(view.lesson()),
            PublicLessonNavigationDto.from(view.navigation()),
            PublicLessonSubscriptionPromptDto.from(view.subscription()),
            PublicLessonAccessMetadataDto.publicPreview()
        );
    }
}
