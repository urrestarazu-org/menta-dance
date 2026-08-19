package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.PhysicalSessionManagementResult;
import com.menta.physical.application.port.in.ListManagedPhysicalSessionsUseCase;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.domain.model.CourseId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ListManagedPhysicalSessionsUseCaseImpl implements ListManagedPhysicalSessionsUseCase {

    private final PhysicalCourseRepository courseRepository;
    private final PhysicalSessionRepository sessionRepository;

    public ListManagedPhysicalSessionsUseCaseImpl(
        PhysicalCourseRepository courseRepository, PhysicalSessionRepository sessionRepository
    ) {
        this.courseRepository = courseRepository;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public List<PhysicalSessionManagementResult> list(
        String courseId, Instant from, Instant to, UUID actingUserId, boolean actingAsAdmin
    ) {
        CourseId parsedCourseId = CourseId.of(courseId);
        CourseOwnershipGuard.resolveOwnedCourse(courseRepository, parsedCourseId, actingUserId, actingAsAdmin);
        return sessionRepository.findManaged(parsedCourseId, from, to).stream()
            .map(PhysicalSessionResultMapper::toResult)
            .toList();
    }
}
