package com.menta.virtual.application.dto;

public record VirtualModuleManagementResult(
    String moduleId, String courseId, String title, boolean preview, int order
) {

    public VirtualModuleManagementResult(String moduleId, String courseId, String title, int order) {
        this(moduleId, courseId, title, false, order);
    }
}
