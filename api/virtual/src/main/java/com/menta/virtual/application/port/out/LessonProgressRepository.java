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

    /** Finds the progress of a user in a lesson; there is at most one row per user and lesson. */
    Optional<LessonProgress> findByUserIdAndLessonId(UUID userId, LessonId lessonId);

    /**
     * Inserts or updates the progress row and returns the persisted state; an update keeps the
     * creation timestamp of the existing row.
     */
    LessonProgress save(LessonProgress progress);

    /** Rows already ordered for resume selection — see {@link CourseProgressRowProjection}. */
    java.util.List<CourseProgressRowProjection> findRowsForUserAndCourse(UUID userId, UUID courseId);

    /**
     * Live denominator for the course-progress percentage (design.md decision 2): only lessons with a video,
     * the ones students can see and play (#851).
     */
    long countLessonsByCourseId(UUID courseId);

    /**
     * Every progress row of the user in a {@code PUBLISHED} course, in one query and already
     * ordered as {@link InProgressCourseRowProjection} describes (#765).
     */
    java.util.List<InProgressCourseRowProjection> findRowsForUserInPublishedCourses(UUID userId);

    /**
     * Same denominator (lessons with video only) as {@link #countLessonsByCourseId} for several courses in one grouped
     * query (#765); a course without lessons is absent from the map.
     */
    java.util.Map<UUID, Long> countLessonsByCourseIds(java.util.Collection<UUID> courseIds);

    /**
     * Deletes the progress of every user in the lesson and returns how many rows it removed; used when
     * the lesson itself is deleted (#830).
     */
    long deleteByLessonId(LessonId lessonId);

    /**
     * Whether any student has progress in a lesson of the course; used to refuse deleting a course that
     * keeps student history (#834).
     */
    boolean existsByCourseId(UUID courseId);
}
