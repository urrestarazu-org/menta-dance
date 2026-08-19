package com.menta.physical.infrastructure.web.dto;

import com.menta.physical.domain.model.SessionStatus;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Partial update — every field is nullable and {@code null} means "not
 * present in this PATCH, leave unchanged". {@code @Positive} is null-safe
 * by Jakarta Bean Validation convention (only fires when a client actually
 * sends a value).
 */
public record UpdatePhysicalSessionRequest(
    LocalDate date,
    LocalTime startTime,
    @Positive Integer capacity,
    String notes,
    SessionStatus status
) {
}
