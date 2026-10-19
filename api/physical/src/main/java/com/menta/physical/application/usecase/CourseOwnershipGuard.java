package com.menta.physical.application.usecase;

import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.domain.exception.CourseNotFoundException;
import com.menta.physical.domain.exception.CourseNotOwnedException;
import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.PhysicalCourse;
import java.util.UUID;

/**
 * Shared ownership resolution for session management use cases
 * (US-PHYSICAL-006): a session has no {@code professorId} of its own, so
 * every write/read against it authorizes through its parent course, the
 * same rule {@code UpdatePhysicalCourseUseCaseImpl} already applies
 * directly to a course.
 */
final class CourseOwnershipGuard {

    private CourseOwnershipGuard() {
    }

    static PhysicalCourse resolveOwnedCourse(
        PhysicalCourseRepository courseRepository, CourseId courseId, UUID actingUserId, boolean actingAsAdmin
    ) {
        PhysicalCourse course = courseRepository.findById(courseId).orElseThrow(CourseNotFoundException::new);
        if (!actingAsAdmin && !course.isOwnedBy(actingUserId)) {
            throw new CourseNotOwnedException();
        }
        return course;
    }
}
