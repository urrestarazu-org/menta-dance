package com.menta.physical.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A confirmed check-in for a {@link PhysicalSession} by a known student
 * (US-PHYSICAL-001). One row per {@code (sessionId, userId)} pair —
 * uniqueness is enforced both at the schema level (see
 * {@code V15__physical_attendances.sql}) and at the use-case level
 * (idempotent re-scan returns the existing row before the INSERT is even
 * attempted).
 *
 * <p>This aggregate is intentionally tiny. The MVP never reuses
 * {@code deviceId} past the {@code recordedAt} snapshot — a deeper audit
 * story (which device bumped which session on which provider) is a separate
 * backlog concern. The fields below are the contract every downstream
 * reader (BFF dashboard, Recepción manual fallback) needs to make sense
 * of a row:</p>
 * <ul>
 *   <li>{@code sessionId} — where this check-in counts.</li>
 *   <li>{@code userId} — who did the check-in.</li>
 *   <li>{@code recordedAt} — when the door accepted it.</li>
 *   <li>{@code deviceId} — which reader saw it (caller-supplied, not
 *       validated against a registry in the MVP).</li>
 *   <li>{@code kind} — how it got there ({@link AttendanceKind}; QR is the
 *       only wired variant today, MANUAL is reserved for #45).</li>
 * </ul>
 */
public final class Attendance {

    private final AttendanceId id;
    private final SessionId sessionId;
    private final java.util.UUID userId;
    private final Instant recordedAt;
    private final String deviceId;
    private final AttendanceKind kind;

    public Attendance(
        AttendanceId id,
        SessionId sessionId,
        java.util.UUID userId,
        Instant recordedAt,
        String deviceId,
        AttendanceKind kind
    ) {
        if (id == null) {
            throw new IllegalArgumentException("id cannot be null");
        }
        this.id = id;
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId cannot be null");
        this.userId = Objects.requireNonNull(userId, "userId cannot be null");
        this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt cannot be null");
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId cannot be null or blank");
        }
        this.deviceId = deviceId;
        this.kind = Objects.requireNonNull(kind, "kind cannot be null");
    }

    /** Factory a use case calls after a successful INSERT. */
    public static Attendance record(
        SessionId sessionId,
        java.util.UUID userId,
        Instant recordedAt,
        String deviceId,
        AttendanceKind kind
    ) {
        return new Attendance(
            AttendanceId.generate(), sessionId, userId, recordedAt, deviceId, kind
        );
    }

    public AttendanceId getId() {
        return id;
    }

    public SessionId getSessionId() {
        return sessionId;
    }

    public java.util.UUID getUserId() {
        return userId;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public AttendanceKind getKind() {
        return kind;
    }
}
