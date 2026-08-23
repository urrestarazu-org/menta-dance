package com.menta.physical.application.dto;

import com.menta.physical.domain.model.AttendanceId;
import com.menta.physical.domain.model.AttendanceKind;
import com.menta.physical.domain.model.SessionId;
import java.time.Instant;
import java.util.UUID;

/**
 * Application-layer view returned by
 * {@link com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase}.
 * Carries every field the persistence layer stored so the BFF or
 * dashboard can re-derive a fuller representation without an extra round
 * trip (US-PHYSICAL-001 escenarios 2 + 5).
 *
 * <p>The web layer projects this into a smaller response DTO
 * ({@code CheckInResponse}) that exposes only {@code attendanceId},
 * {@code recordedAt}, {@code kind} — the rest is for in-process or
 * dashboard consumption.</p>
 */
public record AttendanceView(
    AttendanceId attendanceId,
    SessionId sessionId,
    UUID userId,
    Instant recordedAt,
    String deviceId,
    AttendanceKind kind
) {
}
