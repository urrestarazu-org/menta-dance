package com.menta.physical.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.time.LocalTime;

/** {@code capacity} omitted means "inherit the parent course's capacity" (US-PHYSICAL-006). */
public record CreatePhysicalSessionRequest(
    @NotNull LocalDate date,
    @NotNull LocalTime startTime,
    @Positive Integer capacity,
    String notes
) {
}
