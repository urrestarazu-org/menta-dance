package com.menta.virtual.application.dto;

import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.LessonId;
import com.menta.virtual.domain.model.ModuleId;

/**
 * Compact reference to a sibling lesson inside the same module, used to
 * populate {@link PublicLessonNavigation}. Carries title + isFree so the
 * client can render "previous" and "next" hovers without a second round
 * trip; deliberately omits {@code videoId}, {@code durationMinutes} and
 * other heavyweight fields to keep the public response small.
 */
public record PublicLessonNavigationRef(
    String lessonId,
    String title,
    boolean isFree
) {

    public static PublicLessonNavigationRef of(LessonId id, String title, boolean isFree) {
        return new PublicLessonNavigationRef(id.toString(), title, isFree);
    }
}
