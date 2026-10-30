package com.menta.physical.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Spring Data interface projection for the monthly attendance JPQL query (#39, US-PHYSICAL-002,
 * design C1) — the same split {@code CourseProgressRowProjection} uses in {@code api/virtual}:
 * the out-port never returns a persistence type, so
 * {@code PhysicalCapacityAssignmentRepositoryAdapter} maps this to the application-layer
 * {@code AttendanceHistoryRow}.
 */
public interface MonthlyAttendanceRowProjection {

    /** Id of the assigned session. */
    UUID getSessionId();

    /** Instant at which the session starts. */
    Instant getScheduledAt();

    /** Title of the session's course. */
    String getCourseName();

    /** Display name of the professor who owns the session's course. */
    String getInstructorName();

    /** Instant the check-in was recorded; {@code null} when the student has no attendance for the session. */
    Instant getRecordedAt();

    /** Stored origin of the check-in ({@code QR} or {@code MANUAL}); {@code null} when there is no attendance row. */
    String getKind();
}
