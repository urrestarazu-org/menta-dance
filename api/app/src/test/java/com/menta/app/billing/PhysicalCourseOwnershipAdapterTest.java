package com.menta.app.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for the cross-module delegation itself (US-BILLING-009) —
 * the end-to-end wiring through Physical's real repository is exercised by
 * {@code PhysicalCoursePricingIntegrationTest}.
 */
class PhysicalCourseOwnershipAdapterTest {

    @Test
    void delegates_directly_to_physicals_entry_port() {
        com.menta.physical.application.port.in.PhysicalCourseOwnershipPort physicalPort =
            mock(com.menta.physical.application.port.in.PhysicalCourseOwnershipPort.class);
        UUID professorId = UUID.randomUUID();
        when(physicalPort.findProfessorId("course-1")).thenReturn(Optional.of(professorId));

        PhysicalCourseOwnershipAdapter adapter = new PhysicalCourseOwnershipAdapter(physicalPort);

        assertThat(adapter.findProfessorId("course-1")).contains(professorId);
    }

    @Test
    void propagates_an_empty_result_when_physical_does_not_know_the_course() {
        com.menta.physical.application.port.in.PhysicalCourseOwnershipPort physicalPort =
            mock(com.menta.physical.application.port.in.PhysicalCourseOwnershipPort.class);
        when(physicalPort.findProfessorId("missing")).thenReturn(Optional.empty());

        PhysicalCourseOwnershipAdapter adapter = new PhysicalCourseOwnershipAdapter(physicalPort);

        assertThat(adapter.findProfessorId("missing")).isEmpty();
    }
}
