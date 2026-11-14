package com.menta.bff.application.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * What the management form submits to create one session. The date and time are the ones in the academy's zone;
 * the use case converts them to UTC (ADR-0048).
 *
 * @param localDate date in the academy's zone
 * @param localTime start time in the academy's zone
 * @param capacity  spots of the session, or {@code null} to inherit the course's
 * @param notes     free-text notes, or {@code null}
 */
public record CreateSessionCommand(LocalDate localDate, LocalTime localTime, Integer capacity, String notes) {
}
