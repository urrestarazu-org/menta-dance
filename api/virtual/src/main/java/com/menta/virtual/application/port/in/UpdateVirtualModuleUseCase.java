package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.UpdateVirtualModuleCommand;
import com.menta.virtual.application.dto.VirtualModuleManagementResult;
import java.util.UUID;

public interface UpdateVirtualModuleUseCase {

    VirtualModuleManagementResult update(
        String moduleId, UpdateVirtualModuleCommand command, UUID actingUserId, boolean actingAsAdmin
    );
}
