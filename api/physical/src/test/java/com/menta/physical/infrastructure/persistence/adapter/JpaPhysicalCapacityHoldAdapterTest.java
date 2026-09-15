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
import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityHoldJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityHoldJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * Validates the JPA adapter that owns the {@code physical_capacity_holds}
 * write path (#208, US-PHYSICAL-004b, design B4) — the sibling of
 * {@link JpaPhysicalCapacityAssignmentAdapter} extended with a third locking
 * read so a hold cannot oversell a session an assignment already holds, and
 * vice versa.
 */
class JpaPhysicalCapacityHoldAdapterTest {

    private static final UUID SESSION_UUID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID PAYMENT_UUID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final Instant NOW = Instant.parse("2026-08-24T13:00:00Z");
    private static final Instant EXPIRES_AT = NOW.plusSeconds(1800);

    private PhysicalCapacityHoldJpaRepository holdRepository;
    private PhysicalCapacityAssignmentJpaRepository assignmentRepository;
    private PhysicalSessionJpaRepository sessionRepository;
    private JpaPhysicalCapacityHoldAdapter adapter;

    @BeforeEach
    void setUp() {
        holdRepository = mock(PhysicalCapacityHoldJpaRepository.class);
        assignmentRepository = mock(PhysicalCapacityAssignmentJpaRepository.class);
        sessionRepository = mock(PhysicalSessionJpaRepository.class);
        when(holdRepository.save(any(PhysicalCapacityHoldJpaEntity.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        com.menta.physical.application.port.out.Clock clock = () -> NOW;
        adapter = new JpaPhysicalCapacityHoldAdapter(
            holdRepository, assignmentRepository, sessionRepository, clock
        );
    }

    private void stubSessionWithCapacity(int capacity, long assigned, long activeHolds) {
        when(sessionRepository.lockCapacityForUpdate(SESSION_UUID)).thenReturn(Optional.of(capacity));
        when(assignmentRepository.countBySessionIdForUpdate(SESSION_UUID)).thenReturn(assigned);
        when(holdRepository.countActiveBySessionIdForUpdate(SESSION_UUID, NOW)).thenReturn(activeHolds);
    }

    @Nested
    @DisplayName("Write method persists a row matching the V20.1 schema columns")
    class WriteShape {

        @Test
        void assertHold_saves_one_row_when_assigned_plus_activeHolds_plus_one_does_not_exceed_capacity() {
            stubSessionWithCapacity(2, 0, 0);

            Instant returned = adapter.assertHold(SESSION_UUID, PAYMENT_UUID, EXPIRES_AT);

            assertThat(returned).isEqualTo(NOW);

            ArgumentCaptor<PhysicalCapacityHoldJpaEntity> captor = ArgumentCaptor.forClass(
                PhysicalCapacityHoldJpaEntity.class
            );
            verify(holdRepository).save(captor.capture());
            verify(holdRepository).flush();

            PhysicalCapacityHoldJpaEntity row = captor.getValue();
            assertThat(row.getId()).isNotNull();
            assertThat(row.getSessionId()).isEqualTo(SESSION_UUID);
            assertThat(row.getPaymentId()).isEqualTo(PAYMENT_UUID);
            assertThat(row.getExpiresAt()).isEqualTo(EXPIRES_AT);
            assertThat(row.getConvertedAt()).isNull();
            assertThat(row.getCreatedAt()).isEqualTo(NOW);
        }

        /**
         * Decide-then-insert: a claim that loses the race writes nothing at
         * all, so there is no row to delete either.
         */
        @Test
        void assertHold_writes_nothing_when_assigned_plus_activeHolds_plus_one_exceeds_capacity() {
            stubSessionWithCapacity(1, 1, 0);

            assertThatThrownBy(() -> adapter.assertHold(SESSION_UUID, PAYMENT_UUID, EXPIRES_AT))
                .isInstanceOf(CapacityBelowAssignedException.class);

            verify(holdRepository, never()).save(any(PhysicalCapacityHoldJpaEntity.class));
        }

        @Test
        void assertHold_writes_nothing_when_an_active_hold_alone_exhausts_capacity() {
            stubSessionWithCapacity(1, 0, 1);

            assertThatThrownBy(() -> adapter.assertHold(SESSION_UUID, PAYMENT_UUID, EXPIRES_AT))
                .isInstanceOf(CapacityBelowAssignedException.class);

            verify(holdRepository, never()).save(any(PhysicalCapacityHoldJpaEntity.class));
        }

        @Test
        void assertHold_throws_SessionNotFound_when_the_locking_read_finds_no_row() {
            when(sessionRepository.lockCapacityForUpdate(SESSION_UUID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adapter.assertHold(SESSION_UUID, PAYMENT_UUID, EXPIRES_AT))
                .isInstanceOf(SessionNotFoundException.class);

            verify(holdRepository, never()).save(any(PhysicalCapacityHoldJpaEntity.class));
        }
    }

    /**
     * Design B4/D3: the hold invariant is decided from THREE locking reads,
     * in a fixed order, never a plain read first. A regression here is
     * invisible to any single-threaded assertion on the outcome, exactly
     * like issue #216 for the assignment adapter.
     */
    @Nested
    @DisplayName("The hold decision is taken from three locking reads only, in order")
    class LockingContract {

        @Test
        void the_three_locking_reads_run_in_order_before_the_insert() {
            stubSessionWithCapacity(2, 0, 0);

            adapter.assertHold(SESSION_UUID, PAYMENT_UUID, EXPIRES_AT);

            InOrder order = inOrder(sessionRepository, assignmentRepository, holdRepository);
            order.verify(sessionRepository).lockCapacityForUpdate(SESSION_UUID);
            order.verify(assignmentRepository).countBySessionIdForUpdate(SESSION_UUID);
            order.verify(holdRepository).countActiveBySessionIdForUpdate(SESSION_UUID, NOW);
            order.verify(holdRepository).save(any(PhysicalCapacityHoldJpaEntity.class));
        }

        /**
         * The plain (non-locking) assignment count answers from the
         * transaction's MVCC snapshot and can report a stale value once any
         * earlier non-locking read has fixed it. Here it reports a stale 0
         * while the locking count reports the true 1: the adapter must
         * refuse and never even consult the plain count.
         */
        @Test
        void the_plain_non_locking_assignment_count_is_never_consulted() {
            when(sessionRepository.lockCapacityForUpdate(SESSION_UUID)).thenReturn(Optional.of(1));
            when(assignmentRepository.countBySessionId(SESSION_UUID)).thenReturn(0L);
            when(assignmentRepository.countBySessionIdForUpdate(SESSION_UUID)).thenReturn(1L);
            when(holdRepository.countActiveBySessionIdForUpdate(SESSION_UUID, NOW)).thenReturn(0L);

            assertThatThrownBy(() -> adapter.assertHold(SESSION_UUID, PAYMENT_UUID, EXPIRES_AT))
                .isInstanceOf(CapacityBelowAssignedException.class);

            verify(assignmentRepository, never()).countBySessionId(any(UUID.class));
        }
    }

    @Nested
    @DisplayName("markConverted / release")
    class ConversionAndRelease {

        @Test
        void markConverted_saves_the_matching_row_with_convertedAt_set() {
            PhysicalCapacityHoldJpaEntity existing = new PhysicalCapacityHoldJpaEntity(
                UUID.randomUUID(), SESSION_UUID, PAYMENT_UUID, EXPIRES_AT, null, NOW.minusSeconds(60)
            );
            when(holdRepository.findByPaymentIdOrdered(PAYMENT_UUID)).thenReturn(List.of(existing));

            adapter.markConverted(PAYMENT_UUID, SESSION_UUID);

            ArgumentCaptor<PhysicalCapacityHoldJpaEntity> captor = ArgumentCaptor.forClass(
                PhysicalCapacityHoldJpaEntity.class
            );
            verify(holdRepository).save(captor.capture());
            verify(holdRepository).flush();
            assertThat(captor.getValue().getConvertedAt()).isEqualTo(NOW);
            assertThat(captor.getValue().getId()).isEqualTo(existing.getId());
        }

        @Test
        void markConverted_fails_loudly_when_no_matching_hold_row_exists() {
            when(holdRepository.findByPaymentIdOrdered(PAYMENT_UUID)).thenReturn(List.of());

            assertThatThrownBy(() -> adapter.markConverted(PAYMENT_UUID, SESSION_UUID))
                .isInstanceOf(IllegalStateException.class);

            verify(holdRepository, never()).save(any(PhysicalCapacityHoldJpaEntity.class));
        }

        @Test
        void release_deletes_every_hold_row_for_the_payment() {
            PhysicalCapacityHoldJpaEntity row1 = new PhysicalCapacityHoldJpaEntity(
                UUID.randomUUID(), SESSION_UUID, PAYMENT_UUID, EXPIRES_AT, null, NOW
            );
            when(holdRepository.findByPaymentIdOrdered(PAYMENT_UUID)).thenReturn(List.of(row1));

            adapter.release(PAYMENT_UUID);

            verify(holdRepository).deleteAll(List.of(row1));
            verify(holdRepository).flush();
        }

        @Test
        void release_is_a_noop_when_no_hold_rows_exist_for_the_payment() {
            when(holdRepository.findByPaymentIdOrdered(PAYMENT_UUID)).thenReturn(List.of());

            adapter.release(PAYMENT_UUID);

            verify(holdRepository).deleteAll(List.of());
            verify(holdRepository).flush();
        }
    }
}
