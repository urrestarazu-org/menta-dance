package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.ReorderVirtualModulesCommand;
import com.menta.virtual.application.dto.VirtualModuleManagementResult;
import com.menta.virtual.application.port.in.ReorderVirtualModulesUseCase;
import com.menta.virtual.application.port.out.VirtualCourseAuditRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.domain.exception.ModuleReorderMismatchException;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.ModuleId;
import com.menta.virtual.domain.model.VirtualModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class ReorderVirtualModulesUseCaseImpl implements ReorderVirtualModulesUseCase {

    private final VirtualCourseRepository courseRepository;
    private final VirtualModuleRepository moduleRepository;
    private final VirtualCourseAuditRepository auditRepository;

    public ReorderVirtualModulesUseCaseImpl(
        VirtualCourseRepository courseRepository, VirtualModuleRepository moduleRepository,
        VirtualCourseAuditRepository auditRepository
    ) {
        this.courseRepository = courseRepository;
        this.moduleRepository = moduleRepository;
        this.auditRepository = auditRepository;
    }

    @Override
    public List<VirtualModuleManagementResult> reorder(
        String courseId, ReorderVirtualModulesCommand command, UUID actingUserId, boolean actingAsAdmin
    ) {
        CourseId parsedCourseId = CourseId.of(courseId);
        CourseOwnershipGuard.resolveOwnedCourse(courseRepository, parsedCourseId, actingUserId, actingAsAdmin);

        List<VirtualModule> currentModules = moduleRepository.findByCourseId(parsedCourseId);
        List<ModuleId> requestedOrder = command.moduleIdsInOrder().stream().map(ModuleId::of).toList();

        Set<ModuleId> currentIds = currentModules.stream().map(VirtualModule::getId).collect(Collectors.toSet());
        Set<ModuleId> requestedIds = Set.copyOf(requestedOrder);
        if (!currentIds.equals(requestedIds) || requestedOrder.size() != currentModules.size()) {
            throw new ModuleReorderMismatchException();
        }

        java.util.Map<ModuleId, VirtualModule> byId =
            currentModules.stream().collect(Collectors.toMap(VirtualModule::getId, m -> m));
        List<VirtualModule> reordered = new ArrayList<>();
        for (int i = 0; i < requestedOrder.size(); i++) {
            reordered.add(byId.get(requestedOrder.get(i)).withOrder(i));
        }
        moduleRepository.saveAll(reordered);
        auditRepository.append(
            parsedCourseId, actingUserId, "REORDER_MODULES", null,
            "order=" + requestedOrder.stream().map(ModuleId::toString).collect(Collectors.joining(","))
        );
        return reordered.stream().map(VirtualModuleResultMapper::toResult).toList();
    }
}
