package com.menta.virtual.infrastructure.web.dto;

import com.menta.virtual.application.dto.PublicLessonPreviewView;

/**
 * Teaser projection for the authenticated-but-unsubscribed branch.
 * Carries everything except {@code videoId}, the module / course nesting
 * and the lesson order; deliberately lighter than the full detail so the
 * server stops short of giving the visitor an upgrade decision they
 * haven't earned.
 */
public record PublicLessonPreviewDto(
    String lessonId,
    String title,
    String description,
    String duration,
    boolean isFree,
    String thumbnailUrl
) {

    public static PublicLessonPreviewDto from(PublicLessonPreviewView view) {
        return new PublicLessonPreviewDto(
            view.lessonId(),
            view.title(),
            view.description(),
            view.duration(),
            view.isFree(),
            view.thumbnailUrl()
        );
    }
}
