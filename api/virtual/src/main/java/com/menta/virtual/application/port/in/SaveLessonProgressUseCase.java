package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.LessonProgressView;
import java.util.UUID;

/** Idempotent upsert of a student's playback position for one lesson (US-VIRTUAL-005). */
public interface SaveLessonProgressUseCase {

    LessonProgressView save(String lessonId, UUID actingUserId, int positionSeconds);
}
