package com.menta.virtual.infrastructure.web.dto;

import com.menta.virtual.application.dto.VirtualModuleManagementResult;

/** Wire response for a module in the management endpoints. */
public record VirtualModuleManagementResponse(
    String moduleId, String courseId, String title, boolean preview, int order
) {

    /** Maps the application result to the wire response. */
    public static VirtualModuleManagementResponse from(VirtualModuleManagementResult result) {
        return new VirtualModuleManagementResponse(
            result.moduleId(), result.courseId(), result.title(), result.preview(), result.order()
        );
    }
}
