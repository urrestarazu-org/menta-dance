package com.menta.bff.application.usecase;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * View model of a session in the management screens, with its start in the academy's zone.
 *
 * @param sessionId      session identifier
 * @param courseId       identifier of the course it belongs to
 * @param startsAt       start of the session, for the machine-readable time
 * @param localDate      date in the academy's zone, to prefill the edit form
 * @param localTime      start time in the academy's zone, to prefill the edit form
 * @param momentLabel    Spanish label of both, e.g. {@code "martes 17 de noviembre a las 22:00"}
 * @param cancelled      whether the session is cancelled
 * @param capacity       total spots
 * @param occupied       spots that count against the capacity: confirmed assignments plus live holds
 * @param availableSpots spots still free
 * @param notes          free-text notes, may be {@code null}
 */
public record ManagedSessionView(
        String sessionId, String courseId, Instant startsAt, LocalDate localDate, LocalTime localTime,
        String momentLabel, boolean cancelled, int capacity, int occupied, int availableSpots, String notes) {
}
