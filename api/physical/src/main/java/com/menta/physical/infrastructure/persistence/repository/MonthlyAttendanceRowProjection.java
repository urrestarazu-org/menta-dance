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

    UUID getSessionId();

    Instant getScheduledAt();

    String getCourseName();

    String getInstructorName();

    Instant getRecordedAt();
}
