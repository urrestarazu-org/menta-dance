package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.PhysicalCourseManagementResult;
import com.menta.physical.application.port.in.ListManagedPhysicalCoursesUseCase;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.domain.model.PhysicalCourse;
import java.util.List;
import java.util.UUID;

/**
 * Lists the courses a caller manages: every course for administrators, only their own for
 * instructors.
 *
 * <p>Courses are listed whatever their status, so inactive ones remain visible to their managers.
 */
public class ListManagedPhysicalCoursesUseCaseImpl implements ListManagedPhysicalCoursesUseCase {

    private final PhysicalCourseRepository courseRepository;

    public ListManagedPhysicalCoursesUseCaseImpl(PhysicalCourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Override
    public List<PhysicalCourseManagementResult> list(UUID actingUserId, boolean actingAsAdmin) {
        List<PhysicalCourse> courses = actingAsAdmin
            ? courseRepository.findAll()
            : courseRepository.findByProfessorId(actingUserId);
        return courses.stream().map(PhysicalCourseResultMapper::toResult).toList();
    }
}
