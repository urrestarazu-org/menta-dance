package com.menta.physical.application.usecase;

import com.menta.physical.application.port.in.PhysicalCourseOwnershipPort;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.PhysicalCourse;
import java.util.Optional;
import java.util.UUID;

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
