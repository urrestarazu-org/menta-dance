package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.CreatePhysicalCourseCommand;
import com.menta.physical.application.dto.PhysicalCourseManagementResult;
import com.menta.physical.application.port.in.CreatePhysicalCourseUseCase;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.domain.exception.ProfessorMismatchException;
import com.menta.physical.domain.model.PhysicalCourse;
import java.util.UUID;

public class CreatePhysicalCourseUseCaseImpl implements CreatePhysicalCourseUseCase {

    private final PhysicalCourseRepository courseRepository;

    public CreatePhysicalCourseUseCaseImpl(PhysicalCourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Override
    public PhysicalCourseManagementResult create(
        CreatePhysicalCourseCommand command, UUID actingUserId, boolean actingAsAdmin
    ) {
        UUID effectiveProfessorId = resolveProfessorId(command.professorId(), actingUserId, actingAsAdmin);
        PhysicalCourse course = PhysicalCourse.create(
            command.title(), command.description(), effectiveProfessorId, command.professorName(),
            command.dayOfWeek(), command.startTime(), command.durationMinutes(), command.level(),
            command.capacity()
        );
        return PhysicalCourseResultMapper.toResult(courseRepository.save(course));
    }

    private static UUID resolveProfessorId(UUID requestedProfessorId, UUID actingUserId, boolean actingAsAdmin) {
        if (actingAsAdmin) {
            return requestedProfessorId != null ? requestedProfessorId : actingUserId;
        }
        if (requestedProfessorId != null && !requestedProfessorId.equals(actingUserId)) {
            throw new ProfessorMismatchException();
        }
        return actingUserId;
    }
}
