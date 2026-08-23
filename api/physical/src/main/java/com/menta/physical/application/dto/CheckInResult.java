package com.menta.physical.application.dto;

/**
 * Application-layer result of {@link
 * com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase}.
 * Wraps {@link AttendanceView} without changing it because {@code
 * AttendanceView} alone cannot express "this check-in already existed"
 * (US-PHYSICAL-001 escenario 5) — the web layer needs {@code newlyRecorded}
 * to answer {@code 201 Created} on the first scan and {@code 200 OK} on
 * every idempotent replay of the same QR.
 */
public record CheckInResult(AttendanceView attendance, boolean newlyRecorded) {
}
