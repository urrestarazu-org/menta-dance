package com.menta.bff.application.dto;

/**
 * What the new-module form submits. A {@code null} {@code preview} is not sent and the API takes {@code false}; a
 * {@code null} {@code order} appends the module at the end of the course.
 */
public record CreateVirtualModuleCommand(String title, Boolean preview, Integer order) {
}
