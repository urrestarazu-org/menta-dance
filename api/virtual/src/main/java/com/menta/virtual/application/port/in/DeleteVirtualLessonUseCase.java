package com.menta.virtual.application.port.in;

import java.util.UUID;

/** Inbound port for deleting a lesson (#830). */
public interface DeleteVirtualLessonUseCase {

    /**
     * Deletes a lesson, authorized through its parent course like the edit: an ADMIN deletes any lesson,
     * an INSTRUCTOR only the lessons of the courses they own ({@code CourseNotOwnedException} otherwise).
     * An unknown lesson fails with {@code LessonNotFoundException} and a malformed id with {@code
     * IllegalArgumentException}.
     *
     * <p>In the same transaction it also deletes every student progress row of the lesson (cascade: a
     * progress row of a lesson that no longer exists has nothing to point to, and {@code
     * virtual_lesson_progress} holds no payment or audit data), renumbers the remaining lessons of the
     * module to a contiguous {@code order} 0..n-1 and appends a {@code DELETE_LESSON} audit row with the
     * old snapshot. The course status is not checked: a lesson of a {@code PUBLISHED} course can be
     * deleted, as it can be edited.</p>
     */
    void delete(String lessonId, UUID actingUserId, boolean actingAsAdmin);
}
