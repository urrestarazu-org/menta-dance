package com.menta.virtual.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/** {@code order} is {@code null} to append at the end (US-VIRTUAL-006 escenario 2). */
public record CreateVirtualModuleRequest(@NotBlank String title, @PositiveOrZero Integer order) {
}
