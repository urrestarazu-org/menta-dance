package com.menta.virtual.infrastructure.web.dto;

import com.menta.virtual.application.dto.VirtualModuleManagementResult;

public record VirtualModuleManagementResponse(String moduleId, String courseId, String title, int order) {

    public static VirtualModuleManagementResponse from(VirtualModuleManagementResult result) {
        return new VirtualModuleManagementResponse(
            result.moduleId(), result.courseId(), result.title(), result.order()
        );
    }
}
