package com.menta.virtual.infrastructure.web.dto;

import jakarta.validation.constraints.PositiveOrZero;

/**
 * Request body for editing a module; a {@code null} field is mapped to an empty {@code Optional}
 * and leaves the module value unchanged.
 */
public record UpdateVirtualModuleRequest(String title, Boolean preview, @PositiveOrZero Integer order) {

    public UpdateVirtualModuleRequest(String title, Integer order) {
        this(title, null, order);
    }
}
