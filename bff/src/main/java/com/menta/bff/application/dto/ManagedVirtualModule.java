package com.menta.bff.application.dto;

/** A module as the management API reports it after a write ({@code VirtualModuleManagementResponse}). */
public record ManagedVirtualModule(String moduleId, String courseId, String title, boolean preview, int order) {
}
