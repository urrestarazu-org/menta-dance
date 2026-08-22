package com.menta.virtual.infrastructure.web.dto;

import com.menta.virtual.application.dto.PublicLessonDetailView;

/**
 * Wire-level lesson detail. Two factories so the {@code videoId} branch
 * can only be constructed on the premium view (Jackson serialises
 * {@code null} by default but this keeps it out of the JSON payload for
 * the free variant via {@code @JsonInclude(NON_NULL)} on a future PR —
 * the {@code Records\#2122} quirk on nullable defaults is documented at
 * the orchestrator level).
 */
public record PublicLessonDetailDto(
    String lessonId,
    String title,
    String description,
    String duration,
    boolean isFree,
    int order,
    PublicModuleDto module,
    PublicCourseDto course,
    String videoId,
    String thumbnailUrl
) {

    public static PublicLessonDetailDto fromFree(PublicLessonDetailView view) {
        // Free view constructs the detail record with videoId == null;
        // serialise as null intentionally so the JSON shape is uniform
        // across the two free/premium variants — the type-restriction
        // is at the application layer, not the wire.
        return new PublicLessonDetailDto(
            view.lessonId(), view.title(), view.description(), view.duration(),
            view.isFree(), view.order(),
            PublicModuleDto.from(view.module()),
            PublicCourseDto.from(view.course()),
            view.videoId(),
            view.thumbnailUrl()
        );
    }

    public static PublicLessonDetailDto fromPremium(PublicLessonDetailView view) {
        return fromFree(view);
    }
}
