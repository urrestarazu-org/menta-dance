package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.PhysicalCourseManagementResult;
import com.menta.physical.domain.model.PhysicalCourse;

final class PhysicalCourseResultMapper {

    private PhysicalCourseResultMapper() {
    }

    static PhysicalCourseManagementResult toResult(PhysicalCourse course) {
        return new PhysicalCourseManagementResult(
            course.getId().toString(),
            course.getTitle(),
            course.getDescription(),
            course.getProfessorId().toString(),
            course.getProfessorName(),
            course.getDayOfWeek().name(),
            course.getStartTime().toString(),
            course.getDurationMinutes(),
            course.getLevel().name(),
            course.getCapacity(),
            course.getStatus().name()
        );
    }
}
