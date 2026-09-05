package com.menta.virtual.application.usecase;

import com.menta.virtual.domain.exception.LessonNotFoundException;
import com.menta.virtual.domain.model.LessonId;

/**
 * Shared anti-enumeration id parsing for the three progress use cases (mirrors
 * {@code GetPublicLessonStreamUseCaseImpl#parseLessonIdOrNull}), collapsing a malformed id into
 * the same {@link LessonNotFoundException} a missing row would produce.
 */
final class LessonProgressAccess {

    private LessonProgressAccess() {
    }

    static LessonId parseLessonIdOrThrow(String rawLessonId) {
        if (rawLessonId == null || rawLessonId.isBlank()) {
            throw new LessonNotFoundException();
        }
        try {
            return LessonId.of(rawLessonId);
        } catch (IllegalArgumentException malformed) {
            throw new LessonNotFoundException();
        }
    }
}
