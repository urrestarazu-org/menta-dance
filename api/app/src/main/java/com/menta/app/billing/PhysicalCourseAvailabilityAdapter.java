package com.menta.app.billing;

import com.menta.billing.application.dto.ScheduledSessionSnapshot;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Implements Billing's {@code
 * com.menta.billing.application.port.out.PhysicalCourseAvailabilityPort} out
 * port by calling Physical's entry port directly (US-BILLING-006) — same
 * cross-module composition pattern as {@link PhysicalCourseOwnershipAdapter}:
 * a plain Java call inside {@code api:app}, never HTTP, RabbitMQ or a shared
 * schema (ADR-0037).
 *
 * <p>Both modules expose a type named {@code PhysicalCourseAvailabilityPort}
 * (Billing's out port and Physical's in port) — fully qualified here since
 * they cannot both be imported under the same simple name. Physical's {@code
 * listSessions} already filters to {@code SCHEDULED} sessions only (verified
 * against {@code PhysicalSessionJpaRepository#findScheduledWithAvailability}'s
 * {@code WHERE s.status = 'SCHEDULED'} clause), so no further filtering is
 * needed here.</p>
 */
@Component
public class PhysicalCourseAvailabilityAdapter
    implements com.menta.billing.application.port.out.PhysicalCourseAvailabilityPort {

    private final com.menta.physical.application.port.in.PhysicalCourseAvailabilityPort physicalCourseAvailabilityPort;

    public PhysicalCourseAvailabilityAdapter(
        com.menta.physical.application.port.in.PhysicalCourseAvailabilityPort physicalCourseAvailabilityPort
    ) {
        this.physicalCourseAvailabilityPort = physicalCourseAvailabilityPort;
    }

    @Override
    public List<ScheduledSessionSnapshot> findScheduledSessions(
        String courseId, Instant periodStart, Instant periodEndExclusive
    ) {
        return physicalCourseAvailabilityPort.listSessions(courseId, periodStart, periodEndExclusive).stream()
            .map(session -> new ScheduledSessionSnapshot(
                session.sessionId(), Instant.parse(session.scheduledAt()), session.availableSpots()
            ))
            .toList();
    }
}
