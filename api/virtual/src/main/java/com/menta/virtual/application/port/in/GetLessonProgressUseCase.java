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

    Optional<LessonProgressView> get(String lessonId, UUID actingUserId);
}
