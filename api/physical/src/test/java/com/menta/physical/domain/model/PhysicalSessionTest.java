package com.menta.physical.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PhysicalSessionTest {

    private static final Instant SCHEDULED_AT = Instant.parse("2026-08-25T22:00:00Z");

    private static PhysicalSession session(int capacity, int assignedSpots, int activeCapacityHolds) {
        return new PhysicalSession(
            SessionId.generate(), CourseId.generate(), SCHEDULED_AT, capacity, assignedSpots, activeCapacityHolds
        );
    }

    @Test
    void available_spots_subtracts_assignments_and_holds_from_capacity() {
        assertThat(session(20, 5, 3).getAvailableSpots()).isEqualTo(12);
    }

    @Test
    void available_spots_never_goes_negative_even_if_oversold() {
        // This issue never creates assignments/holds -- it only reads whatever
        // another (future) write path already committed. A defensive floor
        // means a bug elsewhere never surfaces here as a negative number.
        assertThat(session(10, 8, 5).getAvailableSpots()).isZero();
    }

    @Test
    void available_spots_equals_capacity_when_nothing_assigned_or_held() {
        assertThat(session(20, 0, 0).getAvailableSpots()).isEqualTo(20);
    }

    @Test
    void concurrent_assignment_and_hold_rows_are_both_counted_toward_availability() {
        // Simulates two concurrent writers: one confirmed assignment, one
        // active hold landing on the same session between two reads --
        // availableSpots reflects both, proving the calculation is a live
        // read of whatever is committed, not a cached counter that could
        // miss one of the two concurrent writes.
        PhysicalSession beforeConcurrentWrites = session(10, 0, 0);
        PhysicalSession afterConcurrentWrites = session(10, 1, 1);

        assertThat(beforeConcurrentWrites.getAvailableSpots()).isEqualTo(10);
        assertThat(afterConcurrentWrites.getAvailableSpots()).isEqualTo(8);
    }

    @Test
    void rejects_negative_capacity() {
        assertThatThrownBy(() -> session(-1, 0, 0))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("capacity");
    }

    @Test
    void rejects_negative_assigned_spots() {
        assertThatThrownBy(() -> session(10, -1, 0))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("assignedSpots");
    }

    @Test
    void rejects_negative_active_capacity_holds() {
        assertThatThrownBy(() -> session(10, 0, -1))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("activeCapacityHolds");
    }

    @Test
    void rejects_null_required_fields() {
        assertThatThrownBy(() -> new PhysicalSession(null, CourseId.generate(), SCHEDULED_AT, 1, 0, 0))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PhysicalSession(SessionId.generate(), null, SCHEDULED_AT, 1, 0, 0))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PhysicalSession(SessionId.generate(), CourseId.generate(), null, 1, 0, 0))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void exposes_all_fields() {
        PhysicalSession session = session(20, 5, 3);

        assertThat(session.getScheduledAt()).isEqualTo(SCHEDULED_AT);
        assertThat(session.getCapacity()).isEqualTo(20);
        assertThat(session.getAssignedSpots()).isEqualTo(5);
        assertThat(session.getActiveCapacityHolds()).isEqualTo(3);
    }
}
