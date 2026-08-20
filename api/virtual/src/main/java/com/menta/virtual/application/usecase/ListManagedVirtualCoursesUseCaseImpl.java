package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import com.menta.virtual.application.port.in.ListManagedVirtualCoursesUseCase;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.domain.model.VirtualCourse;
import java.util.List;
import java.util.UUID;

public class ListManagedVirtualCoursesUseCaseImpl implements ListManagedVirtualCoursesUseCase {

    private final VirtualCourseRepository courseRepository;

    public ListManagedVirtualCoursesUseCaseImpl(VirtualCourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Override
    public List<VirtualCourseManagementResult> list(UUID actingUserId, boolean actingAsAdmin) {
        List<VirtualCourse> courses = actingAsAdmin
            ? courseRepository.findAll()
            : courseRepository.findByProfessorId(actingUserId);
        return courses.stream().map(VirtualCourseResultMapper::toResult).toList();
    }
}
