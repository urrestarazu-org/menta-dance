package com.menta.bff.application.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Body of {@code PATCH /api/v1/admin/physical/sessions/{sessionId}}: only the fields that are not {@code null}
 * are sent. Date and time are in UTC.
 *
 * @param date      new UTC date
 * @param startTime new UTC start time
 * @param capacity  new number of spots
 * @param notes     new notes
 * @param status    {@code SCHEDULED} or {@code CANCELLED}
 */
public record SessionChanges(LocalDate date, LocalTime startTime, Integer capacity, String notes, String status) {
}
