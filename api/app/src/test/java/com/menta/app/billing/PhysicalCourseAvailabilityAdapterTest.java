package com.menta.app.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.ScheduledSessionSnapshot;
import com.menta.physical.application.dto.PhysicalSessionAvailability;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for the cross-module delegation itself (US-BILLING-006) — the
 * end-to-end wiring through Physical's real repositories is exercised by
 * {@code PhysicalPurchaseIntegrationTest}.
 */
class PhysicalCourseAvailabilityAdapterTest {

    private static final Instant FROM = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-12-01T00:00:00Z");

    private final com.menta.physical.application.port.in.PhysicalCourseAvailabilityPort physicalPort =
        mock(com.menta.physical.application.port.in.PhysicalCourseAvailabilityPort.class);
    private final PhysicalCourseAvailabilityAdapter adapter = new PhysicalCourseAvailabilityAdapter(physicalPort);

    @Test
    void maps_physicals_scheduled_sessions_to_billings_snapshot() {
        when(physicalPort.listSessions("course-1", FROM, TO)).thenReturn(List.of(
            new PhysicalSessionAvailability("session-1", "course-1", "2026-08-10T10:00:00Z", 10, 4, 1, 5)
        ));

        assertThat(adapter.findScheduledSessions("course-1", FROM, TO)).containsExactly(
            new ScheduledSessionSnapshot("session-1", Instant.parse("2026-08-10T10:00:00Z"), 5)
        );
    }

    @Test
    void delegates_the_owned_sessions_lookup_to_physicals_entry_port() {
        UUID studentId = UUID.randomUUID();
        when(physicalPort.findSessionIdsAssignedTo("course-1", studentId, FROM, TO))
            .thenReturn(Set.of("session-1"));

        assertThat(adapter.findSessionIdsAssignedTo("course-1", studentId, FROM, TO))
            .containsExactly("session-1");
    }
}
