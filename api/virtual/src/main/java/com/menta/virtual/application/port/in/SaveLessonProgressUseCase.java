package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.LessonProgressView;
import java.util.UUID;

/** Idempotent upsert of a student's playback position for one lesson (US-VIRTUAL-005). */
public interface SaveLessonProgressUseCase {

    /**
     * Stores the playback position of the user in the lesson and returns the resulting progress,
     * leaving the completion state untouched. A position outside zero and the lesson's duration
     * fails with {@code InvalidLessonPositionException}; unknown lessons and missing access fail as
     * in {@link CompleteLessonUseCase#complete}.
     */
    LessonProgressView save(String lessonId, UUID actingUserId, int positionSeconds);
}
