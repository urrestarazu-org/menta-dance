package com.menta.virtual.application.usecase;

import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.domain.exception.LessonNotFoundException;
import com.menta.virtual.domain.model.LessonId;
import com.menta.virtual.domain.model.VirtualLesson;
import java.util.UUID;

/**
 * Resolves a lesson and authorizes it through its parent course. Unlike a
 * module, a lesson already carries {@code courseId} denormalized directly
 * (mirrors {@code virtual_lessons.course_id} since #46), so this never needs
 * to hop through the module first.
 */
final class LessonOwnershipGuard {

    private LessonOwnershipGuard() {
    }

    static VirtualLesson resolveOwnedLesson(
        VirtualLessonRepository lessonRepository, VirtualCourseRepository courseRepository, LessonId lessonId,
        UUID actingUserId, boolean actingAsAdmin
    ) {
        VirtualLesson lesson = lessonRepository.findById(lessonId).orElseThrow(LessonNotFoundException::new);
        CourseOwnershipGuard.resolveOwnedCourse(courseRepository, lesson.getCourseId(), actingUserId, actingAsAdmin);
        return lesson;
    }
}
