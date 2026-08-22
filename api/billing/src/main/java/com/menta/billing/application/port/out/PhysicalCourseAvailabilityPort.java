package com.menta.billing.application.port.out;

import com.menta.billing.application.dto.ScheduledSessionSnapshot;
import java.time.Instant;
import java.util.List;

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
     * @param courseId the opaque course id to look up.
     * @param periodStart lower bound (inclusive) of the calendar-month period.
     * @param periodEndExclusive upper bound (exclusive) of the calendar-month period.
     * @return every {@code SCHEDULED} session in {@code [periodStart, periodEndExclusive)},
     *     or an empty list if none are scheduled in that range.
     */
    List<ScheduledSessionSnapshot> findScheduledSessions(
        String courseId, Instant periodStart, Instant periodEndExclusive
    );
}
