package com.menta.physical.application.usecase;

import com.menta.physical.application.port.in.PhysicalCourseOwnershipPort;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.PhysicalCourse;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the owning professor of a course for other modules.
 *
 * <p>Looks the course up regardless of status, so an inactive course still reports its owner; the
 * result is empty only when no course has that id.
 */
public class PhysicalCourseOwnershipPortImpl implements PhysicalCourseOwnershipPort {

    private final PhysicalCourseRepository courseRepository;

    public PhysicalCourseOwnershipPortImpl(PhysicalCourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Override
    public Optional<UUID> findProfessorId(String courseId) {
        return courseRepository.findById(CourseId.of(courseId)).map(PhysicalCourse::getProfessorId);
    }
}
