package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.VirtualModuleManagementResult;
import com.menta.virtual.domain.model.VirtualModule;

final class VirtualModuleResultMapper {

    private VirtualModuleResultMapper() {
    }

    static VirtualModuleManagementResult toResult(VirtualModule module) {
        return new VirtualModuleManagementResult(
            module.getId().toString(), module.getCourseId().toString(), module.getTitle(), module.isPreview(), module.getOrder()
        );
    }
}
