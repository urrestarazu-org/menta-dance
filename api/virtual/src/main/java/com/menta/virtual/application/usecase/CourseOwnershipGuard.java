package com.menta.virtual.application.usecase;

import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.domain.exception.CourseNotFoundException;
import com.menta.virtual.domain.exception.CourseNotOwnedException;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.VirtualCourse;
import java.util.UUID;

/**
 * Shared ownership resolution for course/module/lesson management use cases
 * (US-VIRTUAL-006): neither a module nor a lesson has a {@code professorId}
 * of its own — every write against one authorizes through its parent
 * course, resolved via the {@code courseId} already denormalized on both
 * {@code virtual_modules} and {@code virtual_lessons} (since #46).
 */
final class CourseOwnershipGuard {

    private CourseOwnershipGuard() {
    }

    static VirtualCourse resolveOwnedCourse(
        VirtualCourseRepository courseRepository, CourseId courseId, UUID actingUserId, boolean actingAsAdmin
    ) {
        VirtualCourse course = courseRepository.findById(courseId).orElseThrow(CourseNotFoundException::new);
        if (!actingAsAdmin && !course.isOwnedBy(actingUserId)) {
            throw new CourseNotOwnedException();
        }
        return course;
    }
}
