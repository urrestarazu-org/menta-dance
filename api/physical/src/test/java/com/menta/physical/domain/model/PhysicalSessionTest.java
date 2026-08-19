package com.menta.physical.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.physical.domain.exception.CapacityBelowAssignedException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PhysicalSessionTest {

    private static final Instant SCHEDULED_AT = Instant.parse("2026-08-25T22:00:00Z");

    private static PhysicalSession session(int capacity, int assignedSpots, int activeCapacityHolds) {
        return new PhysicalSession(
            SessionId.generate(), CourseId.generate(), SCHEDULED_AT, capacity, assignedSpots,
            activeCapacityHolds, SessionStatus.SCHEDULED, null
        );
    }

    @Test
    void available_spots_subtracts_assignments_and_holds_from_capacity() {
        assertThat(session(20, 5, 3).getAvailableSpots()).isEqualTo(12);
    }

    @Test
    void available_spots_never_goes_negative_even_if_oversold() {
        assertThat(session(10, 8, 5).getAvailableSpots()).isZero();
    }

    @Test
    void available_spots_equals_capacity_when_nothing_assigned_or_held() {
        assertThat(session(20, 0, 0).getAvailableSpots()).isEqualTo(20);
    }

    @Test
    void concurrent_assignment_and_hold_rows_are_both_counted_toward_availability() {
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
        assertThatThrownBy(() -> new PhysicalSession(
            null, CourseId.generate(), SCHEDULED_AT, 1, 0, 0, SessionStatus.SCHEDULED, null
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PhysicalSession(
            SessionId.generate(), null, SCHEDULED_AT, 1, 0, 0, SessionStatus.SCHEDULED, null
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PhysicalSession(
            SessionId.generate(), CourseId.generate(), null, 1, 0, 0, SessionStatus.SCHEDULED, null
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PhysicalSession(
            SessionId.generate(), CourseId.generate(), SCHEDULED_AT, 1, 0, 0, null, null
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void exposes_all_fields() {
        PhysicalSession session = session(20, 5, 3);

        assertThat(session.getScheduledAt()).isEqualTo(SCHEDULED_AT);
        assertThat(session.getCapacity()).isEqualTo(20);
        assertThat(session.getAssignedSpots()).isEqualTo(5);
        assertThat(session.getActiveCapacityHolds()).isEqualTo(3);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
    }

    @Test
    void create_starts_scheduled_with_zero_assignments_and_holds() {
        CourseId courseId = CourseId.generate();

        PhysicalSession session = PhysicalSession.create(courseId, SCHEDULED_AT, 20, "Nota");

        assertThat(session.getCourseId()).isEqualTo(courseId);
        assertThat(session.getScheduledAt()).isEqualTo(SCHEDULED_AT);
        assertThat(session.getCapacity()).isEqualTo(20);
        assertThat(session.getAssignedSpots()).isZero();
        assertThat(session.getActiveCapacityHolds()).isZero();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
        assertThat(session.getNotes()).isEqualTo("Nota");
        assertThat(session.getAvailableSpots()).isEqualTo(20);
    }

    @Test
    void with_schedule_changes_only_scheduled_at() {
        PhysicalSession session = session(20, 5, 3);
        Instant newScheduledAt = SCHEDULED_AT.plusSeconds(3600);

        PhysicalSession rescheduled = session.withSchedule(newScheduledAt);

        assertThat(rescheduled.getScheduledAt()).isEqualTo(newScheduledAt);
        assertThat(rescheduled.getId()).isEqualTo(session.getId());
        assertThat(rescheduled.getCapacity()).isEqualTo(20);
    }

    @Test
    void with_notes_changes_only_notes() {
        PhysicalSession session = session(20, 0, 0);

        assertThat(session.withNotes("Actualizado").getNotes()).isEqualTo("Actualizado");
    }

    @Test
    void with_capacity_accepts_a_value_at_or_above_assigned_spots() {
        PhysicalSession session = session(20, 5, 3);

        assertThat(session.withCapacity(5).getCapacity()).isEqualTo(5);
        assertThat(session.withCapacity(10).getCapacity()).isEqualTo(10);
    }

    @Test
    void with_capacity_rejects_a_value_below_assigned_spots() {
        PhysicalSession session = session(20, 5, 0);

        assertThatThrownBy(() -> session.withCapacity(4)).isInstanceOf(CapacityBelowAssignedException.class);
    }

    @Test
    void cancel_marks_the_session_cancelled_without_touching_other_fields() {
        PhysicalSession session = session(20, 5, 3);

        PhysicalSession cancelled = session.cancel();

        assertThat(cancelled.getStatus()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(cancelled.getCapacity()).isEqualTo(20);
        assertThat(cancelled.getAssignedSpots()).isEqualTo(5);
    }

    @Test
    void has_occurred_compares_scheduled_at_against_the_given_instant() {
        PhysicalSession session = session(20, 0, 0);

        assertThat(session.hasOccurred(SCHEDULED_AT.plusSeconds(1))).isTrue();
        assertThat(session.hasOccurred(SCHEDULED_AT.minusSeconds(1))).isFalse();
    }
}
