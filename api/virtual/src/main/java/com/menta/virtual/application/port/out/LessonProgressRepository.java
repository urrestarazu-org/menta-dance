package com.menta.virtual.application.port.out;

import com.menta.virtual.domain.model.LessonId;
import com.menta.virtual.domain.model.LessonProgress;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link LessonProgress}. Only the two lesson-level methods needed by
 * Slice 2; the course-aggregate query methods are added by Slice 3 (tasks.md forecast
 * correction) so this port never carries stub methods for a query it does not yet implement.
 */
public interface LessonProgressRepository {

    Optional<LessonProgress> findByUserIdAndLessonId(UUID userId, LessonId lessonId);

    LessonProgress save(LessonProgress progress);
}
