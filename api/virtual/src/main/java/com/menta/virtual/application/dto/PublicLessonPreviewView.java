package com.menta.virtual.application.dto;

/**
 * Slimmer lesson projection used for the "auth + premium + no entitlement"
 * branch. Drops the {@code videoId} (still paid content) and the module /
 * course nesting — just enough for the visitor to read a teaser and decide
 * whether to subscribe.
 */
public record PublicLessonPreviewView(
    String lessonId,
    String title,
    String description,
    String duration,
    boolean isFree,
    String thumbnailUrl
) {
}
