package com.menta.physical.infrastructure.web.dto;

import com.menta.physical.application.dto.AttendanceView;

/**
 * Web-layer projection of {@link AttendanceView} for {@code POST
 * /api/v1/physical/sessions/{sessionId}/check-ins} (US-PHYSICAL-001
 * escenarios 2 + 5). Exposes only {@code attendanceId}, {@code recordedAt}
 * and {@code kind} — the door reader has no use for {@code sessionId},
 * {@code userId} or {@code deviceId} echoed back, and idempotency
 * (201 vs 200) is already carried by the HTTP status the controller sets
 * from {@code CheckInResult.newlyRecorded()}, not by a response field.
 */
public record CheckInResponse(
    String attendanceId,
    String recordedAt,
    String kind
) {

    public static CheckInResponse from(AttendanceView view) {
        return new CheckInResponse(
            view.attendanceId().toString(), view.recordedAt().toString(), view.kind().name()
        );
    }
}
