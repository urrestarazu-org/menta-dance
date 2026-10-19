package com.menta.physical.application.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/** {@code capacity} {@code null} means "inherit the parent course's capacity" (US-PHYSICAL-006). */
public record CreatePhysicalSessionCommand(LocalDate date, LocalTime startTime, Integer capacity, String notes) {
}
