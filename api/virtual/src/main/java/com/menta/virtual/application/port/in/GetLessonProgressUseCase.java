package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.LessonProgressView;
import java.util.Optional;
import java.util.UUID;

/**
 * Resume point for one lesson (US-VIRTUAL-005). {@code Optional.empty()} is reserved for the
 * anti-enumeration case (unknown or malformed lesson id) — a granted access with no saved row
 * yet returns a default zeroed {@link LessonProgressView}, never empty.
 */
public interface GetLessonProgressUseCase {

    /**
     * Returns the saved progress of the user for the lesson, or a zeroed view when none was saved
     * yet; the position is capped at the lesson's current duration. A lesson that requires a
     * subscription the user does not hold fails with {@code ForbiddenLessonAccessException}.
     */
    Optional<LessonProgressView> get(String lessonId, UUID actingUserId);
}
