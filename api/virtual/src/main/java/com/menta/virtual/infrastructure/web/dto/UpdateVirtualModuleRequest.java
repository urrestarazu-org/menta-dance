package com.menta.virtual.infrastructure.web.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record UpdateVirtualModuleRequest(String title, @PositiveOrZero Integer order) {
}
