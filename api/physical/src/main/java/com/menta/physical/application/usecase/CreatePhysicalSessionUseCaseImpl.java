package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.CreatePhysicalSessionCommand;
import com.menta.physical.application.dto.PhysicalSessionManagementResult;
import com.menta.physical.application.port.in.CreatePhysicalSessionUseCase;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.PhysicalCourse;
import com.menta.physical.domain.model.PhysicalSession;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public class CreatePhysicalSessionUseCaseImpl implements CreatePhysicalSessionUseCase {

    private final PhysicalCourseRepository courseRepository;
    private final PhysicalSessionRepository sessionRepository;

    public CreatePhysicalSessionUseCaseImpl(
        PhysicalCourseRepository courseRepository, PhysicalSessionRepository sessionRepository
    ) {
        this.courseRepository = courseRepository;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public PhysicalSessionManagementResult create(
        String courseId, CreatePhysicalSessionCommand command, UUID actingUserId, boolean actingAsAdmin
    ) {
        PhysicalCourse course = CourseOwnershipGuard.resolveOwnedCourse(
            courseRepository, CourseId.of(courseId), actingUserId, actingAsAdmin
        );
        int effectiveCapacity = command.capacity() != null ? command.capacity() : course.getCapacity();
        PhysicalSession session = PhysicalSession.create(
            course.getId(),
            LocalDateTime.of(command.date(), command.startTime()).toInstant(ZoneOffset.UTC),
            effectiveCapacity,
            command.notes()
        );
        return PhysicalSessionResultMapper.toResult(sessionRepository.save(session));
    }
}
