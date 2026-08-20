package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.CreateVirtualModuleCommand;
import com.menta.virtual.application.dto.VirtualModuleManagementResult;
import com.menta.virtual.application.port.in.CreateVirtualModuleUseCase;
import com.menta.virtual.application.port.out.VirtualCourseAuditRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.VirtualCourse;
import com.menta.virtual.domain.model.VirtualModule;
import java.util.UUID;

public class CreateVirtualModuleUseCaseImpl implements CreateVirtualModuleUseCase {

    private final VirtualCourseRepository courseRepository;
    private final VirtualModuleRepository moduleRepository;
    private final VirtualCourseAuditRepository auditRepository;

    public CreateVirtualModuleUseCaseImpl(
        VirtualCourseRepository courseRepository, VirtualModuleRepository moduleRepository,
        VirtualCourseAuditRepository auditRepository
    ) {
        this.courseRepository = courseRepository;
        this.moduleRepository = moduleRepository;
        this.auditRepository = auditRepository;
    }

    @Override
    public VirtualModuleManagementResult create(
        String courseId, CreateVirtualModuleCommand command, UUID actingUserId, boolean actingAsAdmin
    ) {
        CourseId parsedCourseId = CourseId.of(courseId);
        VirtualCourse course =
            CourseOwnershipGuard.resolveOwnedCourse(courseRepository, parsedCourseId, actingUserId, actingAsAdmin);

        int order = command.order().orElseGet(() -> moduleRepository.countByCourseId(parsedCourseId));
        VirtualModule module = VirtualModule.create(course.getId(), command.title(), order);
        VirtualModule saved = moduleRepository.save(module);
        auditRepository.append(
            parsedCourseId, actingUserId, "CREATE_MODULE", null,
            "moduleId=" + saved.getId() + ", title=" + saved.getTitle() + ", order=" + saved.getOrder()
        );
        return VirtualModuleResultMapper.toResult(saved);
    }
}
