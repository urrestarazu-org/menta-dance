package com.menta.virtual.application.port.out;

import com.menta.virtual.domain.model.LessonId;
import com.menta.virtual.domain.model.LessonProgress;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link LessonProgress}. Slice 2 declared the two lesson-level methods;
 * Slice 3 adds the two course-aggregate query methods below in the same PR that implements them
 * (tasks.md forecast correction), so this port never carried stub methods for a query it did not
 * yet implement.
 */
public interface LessonProgressRepository {

    Optional<LessonProgress> findByUserIdAndLessonId(UUID userId, LessonId lessonId);

    LessonProgress save(LessonProgress progress);

    /** Rows already ordered for resume selection — see {@link CourseProgressRowProjection}. */
    java.util.List<CourseProgressRowProjection> findRowsForUserAndCourse(UUID userId, UUID courseId);

    /** Live denominator for the course-progress percentage (design.md decision 2). */
    long countLessonsByCourseId(UUID courseId);
}
