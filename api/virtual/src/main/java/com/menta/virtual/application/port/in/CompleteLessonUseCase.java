package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.LessonProgressView;
import java.util.UUID;

/** Marks a lesson complete without moving its saved position (US-VIRTUAL-005, decision 7). */
public interface CompleteLessonUseCase {

    LessonProgressView complete(String lessonId, UUID actingUserId);
}
