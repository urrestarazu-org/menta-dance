package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.menta.physical.application.port.out.PhysicalCapacityAssignmentWriter;
import com.menta.physical.domain.exception.CapacityBelowAssignedException;
import com.menta.physical.domain.exception.SessionNotFoundException;
import com.menta.shared.physical.CapacityAssignmentCommand;
import com.menta.shared.physical.MultiSessionCapacityAssignmentCommand;
import com.menta.shared.physical.MultiSessionCapacityAssignmentCommand.SessionClaim;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * The capacity invariant is decided by
 * {@link PhysicalCapacityAssignmentWriter#assertAssignment} under two
 * locking reads; this use case only orders the claims and normalises the
 * V7 {@code UNIQUE} race into {@link CapacityBelowAssignedException}, the
 * SOLE capacity exception type per design §5.3.
 *
 * <p><b>Issue #216.</b> This use case used to read the session first and
 * fail fast on {@code assignedSpots >= capacity}. That non-locking read
 * fixed the transaction's MVCC snapshot before the writer's {@code FOR
 * UPDATE} ran, and two concurrent claims oversold a capacity-1 session in
 * 7 of 11 measured runs. The collaborator is gone from the constructor on
 * purpose: there is no session read left to make stale.</p>
 */
class AssignCapacityUseCaseTest {

    private static final UUID SESSION_UUID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID STUDENT_UUID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID PAYMENT_UUID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private PhysicalCapacityAssignmentWriter assignmentWriter;
    private AssignCapacityUseCase useCase;

    @BeforeEach
    void setUp() {
        assignmentWriter = mock(PhysicalCapacityAssignmentWriter.class);
        useCase = new AssignCapacityUseCase(assignmentWriter);
    }

    private static CapacityAssignmentCommand cmd() {
        return new CapacityAssignmentCommand(SESSION_UUID, STUDENT_UUID, PAYMENT_UUID);
    }

    @Nested
    @DisplayName("Spec scenario: Capacity available — the Assignment unblocks QR")
    class CapacityAvailable {

        @Test
        void delegates_the_claim_to_the_writer_and_reports_assigned() {
            AssignmentOutcome result = useCase.assign(cmd());

            assertThat(result).isEqualTo(AssignmentOutcome.ASSIGNED.INSTANCE);
            verify(assignmentWriter).assertAssignment(SESSION_UUID, STUDENT_UUID);
        }
    }

    @Nested
    @DisplayName("Spec scenario: Capacity invariant trips — Purchase flips to EXCEPTION")
    class CapacityTrip {

        @Test
        void propagates_the_writers_capacity_refusal_unchanged() {
            doThrow(new CapacityBelowAssignedException())
                .when(assignmentWriter).assertAssignment(SESSION_UUID, STUDENT_UUID);

            assertThatThrownBy(() -> useCase.assign(cmd()))
                .isInstanceOf(CapacityBelowAssignedException.class);
        }
    }

    @Nested
    @DisplayName("Spec scenario: UNIQUE race on (sessionId, studentId) routes to EXCEPTION")
    class UniqueRace {

        @Test
        void rethrows_DataIntegrityViolation_as_CapacityBelowAssigned() {
            doThrow(new DataIntegrityViolationException(
                "Duplicate entry for key 'uq_physical_assignment_session_student'"))
                .when(assignmentWriter).assertAssignment(SESSION_UUID, STUDENT_UUID);

            assertThatThrownBy(() -> useCase.assign(cmd()))
                .isInstanceOf(CapacityBelowAssignedException.class);
        }
    }

    @Nested
    @DisplayName("Spec scenario: HOLD_EXPIRED / TARGET_NOT_SCHEDULED residual")
    class SessionAbsent {

        /**
         * The missing-session residual survived the removal of the early
         * read: the writer's locking capacity read finds no row and raises
         * it, and this use case must not swallow or re-map it.
         */
        @Test
        void propagates_SessionNotFound_raised_by_the_writer() {
            doThrow(new SessionNotFoundException())
                .when(assignmentWriter).assertAssignment(SESSION_UUID, STUDENT_UUID);

            assertThatThrownBy(() -> useCase.assign(cmd()))
                .isInstanceOf(SessionNotFoundException.class);
        }
    }

    /**
     * PR3 (design A3): {@code assignAll} claims an ordered set of sessions
     * under one transactional boundary, all-or-nothing. {@code assign}
     * degenerates to a singleton call into the same loop.
     */
    @Nested
    @DisplayName("assignAll: ordered, all-or-nothing multi-session claim (design A3)")
    class AssignAll {

        private static final UUID SESSION_UUID_2 = UUID.fromString("66666666-6666-6666-6666-666666666666");
        private static final UUID SESSION_UUID_3 = UUID.fromString("88888888-8888-8888-8888-888888888888");

        private static SessionClaim claim(UUID sessionId, long secondsFromEpoch) {
            return new SessionClaim(sessionId, Instant.ofEpochSecond(secondsFromEpoch));
        }

        private static MultiSessionCapacityAssignmentCommand multiCmd(List<SessionClaim> claims) {
            return new MultiSessionCapacityAssignmentCommand(claims, STUDENT_UUID, PAYMENT_UUID);
        }

        @Test
        @DisplayName("Claims are attempted in list order and all three are assigned")
        void claims_are_attempted_in_list_order() {
            List<SessionClaim> claims = List.of(
                claim(SESSION_UUID, 1), claim(SESSION_UUID_2, 2), claim(SESSION_UUID_3, 3)
            );

            CapacityAssignments result = useCase.assignAll(multiCmd(claims));

            assertThat(result.assignedSessionIds())
                .containsExactly(SESSION_UUID, SESSION_UUID_2, SESSION_UUID_3);

            InOrder inOrder = inOrder(assignmentWriter);
            inOrder.verify(assignmentWriter).assertAssignment(SESSION_UUID, STUDENT_UUID);
            inOrder.verify(assignmentWriter).assertAssignment(SESSION_UUID_2, STUDENT_UUID);
            inOrder.verify(assignmentWriter).assertAssignment(SESSION_UUID_3, STUDENT_UUID);
        }

        @Test
        @DisplayName("A failure at claim k throws and attempts no k+1 insert")
        void failure_at_claim_k_aborts_and_attempts_no_further_insert() {
            doThrow(new CapacityBelowAssignedException())
                .when(assignmentWriter).assertAssignment(SESSION_UUID_2, STUDENT_UUID);

            List<SessionClaim> claims = List.of(
                claim(SESSION_UUID, 1), claim(SESSION_UUID_2, 2), claim(SESSION_UUID_3, 3)
            );

            assertThatThrownBy(() -> useCase.assignAll(multiCmd(claims)))
                .isInstanceOf(CapacityBelowAssignedException.class);

            verify(assignmentWriter).assertAssignment(SESSION_UUID, STUDENT_UUID);
            verify(assignmentWriter, never()).assertAssignment(eq(SESSION_UUID_3), any(UUID.class));
        }

        @Test
        @DisplayName("N=1 through assignAll matches today's assign behavior")
        void n_equal_one_through_assign_all_matches_assign_behavior() {
            CapacityAssignments result = useCase.assignAll(multiCmd(List.of(claim(SESSION_UUID, 1))));

            assertThat(result.assignedSessionIds()).containsExactly(SESSION_UUID);
            verify(assignmentWriter).assertAssignment(SESSION_UUID, STUDENT_UUID);
        }
    }
}
