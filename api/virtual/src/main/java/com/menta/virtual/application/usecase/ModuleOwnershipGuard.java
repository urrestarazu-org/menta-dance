package com.menta.virtual.application.usecase;

import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.domain.exception.ModuleNotFoundException;
import com.menta.virtual.domain.model.ModuleId;
import com.menta.virtual.domain.model.VirtualModule;
import java.util.UUID;

/**
 * Resolves a module and authorizes it through its parent course — a module
 * has no {@code professorId} of its own (US-VIRTUAL-006).
 */
final class ModuleOwnershipGuard {

    private ModuleOwnershipGuard() {
    }

    static VirtualModule resolveOwnedModule(
        VirtualModuleRepository moduleRepository, VirtualCourseRepository courseRepository, ModuleId moduleId,
        UUID actingUserId, boolean actingAsAdmin
    ) {
        VirtualModule module = moduleRepository.findById(moduleId).orElseThrow(ModuleNotFoundException::new);
        CourseOwnershipGuard.resolveOwnedCourse(courseRepository, module.getCourseId(), actingUserId, actingAsAdmin);
        return module;
    }
}
