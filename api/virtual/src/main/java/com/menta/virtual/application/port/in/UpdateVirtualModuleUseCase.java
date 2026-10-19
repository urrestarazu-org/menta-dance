package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.UpdateVirtualModuleCommand;
import com.menta.virtual.application.dto.VirtualModuleManagementResult;
import java.util.UUID;

/** Inbound port for editing a module (US-VIRTUAL-006). */
public interface UpdateVirtualModuleUseCase {

    /**
     * Applies a partial update to the module, authorized through its parent course, and appends an
     * {@code UPDATE_MODULE} audit row with the before and after snapshots. An unknown module fails
     * with {@code ModuleNotFoundException}.
     */
    VirtualModuleManagementResult update(
        String moduleId, UpdateVirtualModuleCommand command, UUID actingUserId, boolean actingAsAdmin
    );
}
