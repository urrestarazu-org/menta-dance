package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.LessonProgressView;
import java.util.UUID;

/** Marks a lesson complete without moving its saved position (US-VIRTUAL-005, decision 7). */
public interface CompleteLessonUseCase {

    /**
     * Marks the lesson complete for the user, creating the progress row when none exists, and
     * returns the resulting progress. A malformed or unknown lesson fails with {@code
     * LessonNotFoundException}, and a lesson that requires a subscription the user does not hold
     * fails with {@code ForbiddenLessonAccessException}.
     */
    LessonProgressView complete(String lessonId, UUID actingUserId);
}
