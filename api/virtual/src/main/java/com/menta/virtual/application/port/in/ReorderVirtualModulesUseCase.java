package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.ReorderVirtualModulesCommand;
import com.menta.virtual.application.dto.VirtualModuleManagementResult;
import java.util.List;
import java.util.UUID;

public interface ReorderVirtualModulesUseCase {

    List<VirtualModuleManagementResult> reorder(
        String courseId, ReorderVirtualModulesCommand command, UUID actingUserId, boolean actingAsAdmin
    );
}
