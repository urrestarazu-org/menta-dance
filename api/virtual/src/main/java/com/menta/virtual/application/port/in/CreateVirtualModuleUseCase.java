package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.CreateVirtualModuleCommand;
import com.menta.virtual.application.dto.VirtualModuleManagementResult;
import java.util.UUID;

public interface CreateVirtualModuleUseCase {

    VirtualModuleManagementResult create(
        String courseId, CreateVirtualModuleCommand command, UUID actingUserId, boolean actingAsAdmin
    );
}
