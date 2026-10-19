package com.menta.billing.application.port.out;

import com.menta.billing.application.dto.ScheduledSessionSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-module read port toward Physical's scheduled-session availability
 * (US-BILLING-006) — {@code api:app} implements this by calling Physical's
 * entry port {@code
 * com.menta.physical.application.port.in.PhysicalCourseAvailabilityPort}
 * directly, same composition pattern as {@code PhysicalCourseOwnershipPort}:
 * a plain Java call, never HTTP, RabbitMQ or a shared schema
 * (docs/25-ARCHITECTURE-RULES.md).
 */
public interface PhysicalCourseAvailabilityPort {

    /**
     * Lists the scheduled sessions of a course within a half-open time range.
     *
     * @param courseId the opaque course id to look up.
     * @param periodStart lower bound (inclusive) of the calendar-month period.
     * @param periodEndExclusive upper bound (exclusive) of the calendar-month period.
     * @return every {@code SCHEDULED} session in {@code [periodStart, periodEndExclusive)},
     *     or an empty list if none are scheduled in that range.
     */
    List<ScheduledSessionSnapshot> findScheduledSessions(
        String courseId, Instant periodStart, Instant periodEndExclusive
    );

    /**
     * Ids of the sessions of a course the student already holds a confirmed
     * assignment for, within the half-open range {@code [from, to)}. A
     * MONTHLY purchase must not cover them again: assigning the same student
     * to the same session twice is rejected by the database.
     *
     * @param courseId the opaque course id to look up.
     * @param studentId the student whose assignments are read.
     * @param from lower bound (inclusive) of the range.
     * @param to upper bound (exclusive) of the range.
     * @return the owned session ids, or an empty set if none.
     */
    Set<String> findSessionIdsAssignedTo(String courseId, UUID studentId, Instant from, Instant to);
}
