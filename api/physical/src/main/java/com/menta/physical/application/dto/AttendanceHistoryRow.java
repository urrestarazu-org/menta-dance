package com.menta.physical.application.dto;

import com.menta.physical.domain.model.AttendanceKind;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of a student's monthly attendance, as returned by
 * {@code PhysicalCapacityAssignmentRepository}'s monthly query (#39, US-PHYSICAL-002, design
 * C1/C6) — one per assignment, joined with the session/course display fields and a
 * {@code LEFT JOIN} on the matching {@code physical_attendances} row.
 *
 * <p>{@code recordedAt} is {@code null} when no attendance row matched (D5) — {@link
 * #attended()} is derived from that presence, never a stored status column. {@code kind} is the
 * origin of that attendance row and is {@code null} exactly when {@code recordedAt} is.</p>
 */
public record AttendanceHistoryRow(
    UUID sessionId, Instant scheduledAt, String courseName, String instructorName, Instant recordedAt,
    AttendanceKind kind
) {

    /**
     * Tells whether the student has an attendance record for this session.
     *
     * <p>Derived from {@code recordedAt} being present; no status is stored anywhere.
     */
    public boolean attended() {
        return recordedAt != null;
    }
}
