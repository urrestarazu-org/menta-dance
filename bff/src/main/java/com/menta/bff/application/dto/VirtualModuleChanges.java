package com.menta.bff.application.dto;

/** The fields of a module to change: a {@code null} field is not sent and the API leaves it as it is. */
public record VirtualModuleChanges(String title, Boolean preview, Integer order) {
}
