package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.UpdateVirtualModuleCommand;
import com.menta.virtual.application.dto.VirtualModuleManagementResult;
import com.menta.virtual.application.port.in.UpdateVirtualModuleUseCase;
import com.menta.virtual.application.port.out.VirtualCourseAuditRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.domain.model.ModuleId;
import com.menta.virtual.domain.model.VirtualModule;
import java.util.UUID;

public class UpdateVirtualModuleUseCaseImpl implements UpdateVirtualModuleUseCase {

    private final VirtualModuleRepository moduleRepository;
    private final VirtualCourseRepository courseRepository;
    private final VirtualCourseAuditRepository auditRepository;

    public UpdateVirtualModuleUseCaseImpl(
        VirtualModuleRepository moduleRepository, VirtualCourseRepository courseRepository,
        VirtualCourseAuditRepository auditRepository
    ) {
        this.moduleRepository = moduleRepository;
        this.courseRepository = courseRepository;
        this.auditRepository = auditRepository;
    }

    @Override
    public VirtualModuleManagementResult update(
        String moduleId, UpdateVirtualModuleCommand command, UUID actingUserId, boolean actingAsAdmin
    ) {
        VirtualModule module = ModuleOwnershipGuard.resolveOwnedModule(
            moduleRepository, courseRepository, ModuleId.of(moduleId), actingUserId, actingAsAdmin
        );
        String before = "title=" + module.getTitle() + ", preview=" + module.isPreview() + ", order=" + module.getOrder();

        VirtualModule updated = module;
        if (command.title().isPresent()) {
            updated = updated.withTitle(command.title().get());
        }
        if (command.order().isPresent()) {
            updated = updated.withOrder(command.order().get());
        }
        if (command.preview().isPresent()) {
            updated = updated.withPreview(command.preview().get());
        }
        VirtualModule saved = moduleRepository.save(updated);
        auditRepository.append(
            saved.getCourseId(), actingUserId, "UPDATE_MODULE", before,
            "title=" + saved.getTitle() + ", preview=" + saved.isPreview() + ", order=" + saved.getOrder()
        );
        return VirtualModuleResultMapper.toResult(saved);
    }
}
