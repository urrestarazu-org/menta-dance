package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.CreateVirtualCourseCommand;
import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import com.menta.virtual.application.port.in.CreateVirtualCourseUseCase;
import com.menta.virtual.application.port.out.VirtualCourseAuditRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.domain.exception.ProfessorMismatchException;
import com.menta.virtual.domain.model.CourseCategory;
import com.menta.virtual.domain.model.CourseLevel;
import com.menta.virtual.domain.model.VirtualCourse;
import java.util.UUID;

public class CreateVirtualCourseUseCaseImpl implements CreateVirtualCourseUseCase {

    private final VirtualCourseRepository courseRepository;
    private final VirtualCourseAuditRepository auditRepository;

    public CreateVirtualCourseUseCaseImpl(
        VirtualCourseRepository courseRepository, VirtualCourseAuditRepository auditRepository
    ) {
        this.courseRepository = courseRepository;
        this.auditRepository = auditRepository;
    }

    @Override
    public VirtualCourseManagementResult create(
        CreateVirtualCourseCommand command, UUID actingUserId, boolean actingAsAdmin
    ) {
        UUID effectiveProfessorId = resolveProfessorId(command.professorId(), actingUserId, actingAsAdmin);
        VirtualCourse course = VirtualCourse.create(
            command.title(), command.shortDescription(), command.description(), effectiveProfessorId,
            command.imageUrl(), CourseCategory.of(command.category()), CourseLevel.valueOf(command.level())
        );
        VirtualCourse saved = courseRepository.save(course);
        auditRepository.append(
            saved.getId(), actingUserId, "CREATE_COURSE", null, VirtualCourseResultMapper.toAuditSnapshot(saved)
        );
        return VirtualCourseResultMapper.toResult(saved);
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
