package com.menta.virtual.infrastructure.web.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record UpdateVirtualModuleRequest(String title, Boolean preview, @PositiveOrZero Integer order) {

    public UpdateVirtualModuleRequest(String title, Integer order) {
        this(title, null, order);
    }
}
