package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.ReorderVirtualModulesCommand;
import com.menta.virtual.application.dto.VirtualModuleManagementResult;
import java.util.List;
import java.util.UUID;

/** Inbound port for reordering the modules of a course (US-VIRTUAL-006). */
public interface ReorderVirtualModulesUseCase {

    /**
     * Rewrites the display order of a course's modules so that the module at position {@code i} of
     * the command gets order {@code i}, then appends a {@code REORDER_MODULES} audit row and
     * returns the modules in the new order.
     *
     * <p>The ids must be exactly the course's current modules, without omissions or duplicates,
     * otherwise {@code ModuleReorderMismatchException} is thrown.
     */
    List<VirtualModuleManagementResult> reorder(
        String courseId, ReorderVirtualModulesCommand command, UUID actingUserId, boolean actingAsAdmin
    );
}
