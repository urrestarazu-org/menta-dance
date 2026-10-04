package com.menta.virtual.application.dto;

/**
 * Management view of a module returned by the module use cases; the four-argument constructor
 * defaults {@code preview} to {@code false}.
 */
public record VirtualModuleManagementResult(
    String moduleId, String courseId, String title, boolean preview, int order
) {

    public VirtualModuleManagementResult(String moduleId, String courseId, String title, int order) {
        this(moduleId, courseId, title, false, order);
    }
}
