package com.menta.physical.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.physical.domain.exception.CapacityBelowAssignedException;
import com.menta.physical.domain.exception.SessionNotFoundException;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityAssignmentJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * Validates the JPA adapter that owns the
 * {@code physical_capacity_assignments} write path (TASK-005), including
 * the locking contract it was given in issue #216.
 */
class JpaPhysicalCapacityAssignmentAdapterTest {

    private static final UUID SESSION_UUID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID STUDENT_UUID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant NOW = Instant.parse("2026-08-24T13:00:00Z");

    private PhysicalCapacityAssignmentJpaRepository assignmentRepository;
    private PhysicalSessionJpaRepository sessionRepository;
    private JpaPhysicalCapacityAssignmentAdapter adapter;

    @BeforeEach
    void setUp() {
        assignmentRepository = mock(PhysicalCapacityAssignmentJpaRepository.class);
        sessionRepository = mock(PhysicalSessionJpaRepository.class);
        when(assignmentRepository.save(any(PhysicalCapacityAssignmentJpaEntity.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        com.menta.physical.application.port.out.Clock clock = () -> NOW;
        adapter = new JpaPhysicalCapacityAssignmentAdapter(
            assignmentRepository, sessionRepository, clock
        );
    }

    private void stubSessionWithCapacity(int capacity, long assignedSpots) {
        when(sessionRepository.lockCapacityForUpdate(SESSION_UUID)).thenReturn(Optional.of(capacity));
        when(assignmentRepository.countBySessionIdForUpdate(SESSION_UUID)).thenReturn(assignedSpots);
    }

    @Nested
    @DisplayName("Write method persists a row matching the V7 schema columns")
    class WriteShape {

        @Test
        void assertAssignment_saves_one_row_when_assignedSpots_plus_one_does_not_exceed_capacity() {
            stubSessionWithCapacity(2, 0);

            Instant returned = adapter.assertAssignment(SESSION_UUID, STUDENT_UUID);

            assertThat(returned).isEqualTo(NOW);

            ArgumentCaptor<PhysicalCapacityAssignmentJpaEntity> captor = ArgumentCaptor.forClass(
                PhysicalCapacityAssignmentJpaEntity.class
            );
            verify(assignmentRepository).save(captor.capture());
            verify(assignmentRepository).flush();

            PhysicalCapacityAssignmentJpaEntity row = captor.getValue();
            assertThat(row.getId()).isNotNull();
            assertThat(row.getSessionId()).isEqualTo(SESSION_UUID);
            assertThat(row.getStudentId()).isEqualTo(STUDENT_UUID);
            assertThat(row.getCreatedAt()).isEqualTo(NOW);
        }

        /**
         * Decide-then-insert, not insert-then-compensate: a claim that loses
         * writes nothing, so there is no row to delete either.
         */
        @Test
        void assertAssignment_writes_nothing_when_assignedSpots_plus_one_exceeds_capacity() {
            stubSessionWithCapacity(1, 1);

            assertThatThrownBy(() -> adapter.assertAssignment(SESSION_UUID, STUDENT_UUID))
                .isInstanceOf(CapacityBelowAssignedException.class);

            verify(assignmentRepository, never()).save(any(PhysicalCapacityAssignmentJpaEntity.class));
            verify(assignmentRepository, never()).deleteById(any(UUID.class));
        }

        @Test
        void assertAssignment_throws_SessionNotFound_when_the_locking_read_finds_no_row() {
            when(sessionRepository.lockCapacityForUpdate(SESSION_UUID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adapter.assertAssignment(SESSION_UUID, STUDENT_UUID))
                .isInstanceOf(SessionNotFoundException.class);

            verify(assignmentRepository, never()).save(any(PhysicalCapacityAssignmentJpaEntity.class));
        }
    }

    /**
     * Issue #216: the shape of the reads is the fix. A regression here is
     * invisible to any single-threaded assertion on the outcome, so it is
     * pinned on the collaborators directly.
     */
    @Nested
    @DisplayName("The capacity decision is taken from locking reads only")
    class LockingContract {

        @Test
        void the_session_row_is_locked_before_the_assignments_are_counted() {
            stubSessionWithCapacity(2, 0);

            adapter.assertAssignment(SESSION_UUID, STUDENT_UUID);

            InOrder inOrder = inOrder(sessionRepository, assignmentRepository);
            inOrder.verify(sessionRepository).lockCapacityForUpdate(SESSION_UUID);
            inOrder.verify(assignmentRepository).countBySessionIdForUpdate(SESSION_UUID);
            inOrder.verify(assignmentRepository).save(any(PhysicalCapacityAssignmentJpaEntity.class));
        }

        /**
         * The plain {@code countBySessionId} answers from the transaction's
         * MVCC snapshot and oversells 2/1 once any earlier non-locking read
         * has fixed it. Here it reports a stale 0 while the locking count
         * reports the true 1: the adapter must refuse.
         */
        @Test
        void the_plain_non_locking_count_is_never_consulted() {
            when(sessionRepository.lockCapacityForUpdate(SESSION_UUID)).thenReturn(Optional.of(1));
            when(assignmentRepository.countBySessionId(SESSION_UUID)).thenReturn(0L);
            when(assignmentRepository.countBySessionIdForUpdate(SESSION_UUID)).thenReturn(1L);

            assertThatThrownBy(() -> adapter.assertAssignment(SESSION_UUID, STUDENT_UUID))
                .isInstanceOf(CapacityBelowAssignedException.class);

            verify(assignmentRepository, never()).countBySessionId(any(UUID.class));
        }
    }
}
