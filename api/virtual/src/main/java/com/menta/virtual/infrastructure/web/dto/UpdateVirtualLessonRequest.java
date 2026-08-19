package com.menta.virtual.infrastructure.web.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record UpdateVirtualLessonRequest(
    String title,
    String description,
    String videoId,
    @PositiveOrZero Integer durationMinutes,
    Boolean free,
    @PositiveOrZero Integer order
) {
}
