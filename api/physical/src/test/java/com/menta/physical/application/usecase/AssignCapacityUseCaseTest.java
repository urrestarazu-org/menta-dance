package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.physical.application.port.out.PhysicalCapacityAssignmentWriter;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.domain.exception.CapacityBelowAssignedException;
import com.menta.physical.domain.exception.SessionNotFoundException;
import com.menta.physical.domain.model.PhysicalSession;
import com.menta.physical.domain.model.SessionId;
import com.menta.shared.physical.CapacityAssignmentCommand;
import com.menta.shared.physical.MultiSessionCapacityAssignmentCommand;
import com.menta.shared.physical.MultiSessionCapacityAssignmentCommand.SessionClaim;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * RED-GREEN: every assertion references the new
 * {@link AssignCapacityUseCase} (proposal §4, design §5.3).
 *
 * <p>The capacity invariant is enforced by reading {@code assignedSpots}
 * live from {@code physical_capacity_assignments} (the domain treats the
 * count as a derived projection, never a cached counter) and refusing the
 * INSERT — propagating both the read-time trip AND V7 {@code UNIQUE}
 * race as {@link CapacityBelowAssignedException}, the SOLE exception type
 * per design §5.3.</p>
 */
class AssignCapacityUseCaseTest {

    private static final UUID SESSION_UUID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID STUDENT_UUID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID PAYMENT_UUID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final SessionId SESSION_ID = SessionId.of(SESSION_UUID);

    private PhysicalSessionRepository sessionRepository;
    private PhysicalCapacityAssignmentWriter assignmentWriter;
    private AssignCapacityUseCase useCase;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(PhysicalSessionRepository.class);
        assignmentWriter = mock(PhysicalCapacityAssignmentWriter.class);
        useCase = new AssignCapacityUseCase(sessionRepository, assignmentWriter);
    }

    private static CapacityAssignmentCommand cmd() {
        return new CapacityAssignmentCommand(SESSION_UUID, STUDENT_UUID, PAYMENT_UUID);
    }

    private static PhysicalSession sessionWithCapacity(int capacity, int assigned) {
        return new PhysicalSession(
            SESSION_ID,
            com.menta.physical.domain.model.CourseId
                .of(UUID.fromString("77777777-7777-7777-7777-777777777777")),
            java.time.Instant.parse("2026-09-16T19:00:00Z"),
            capacity, assigned, 0,
            com.menta.physical.domain.model.SessionStatus.SCHEDULED, null
        );
    }

    @Nested
    @DisplayName("Spec scenario: Capacity available — the Assignment unblocks QR")
    class CapacityAvailable {

        @Test
        void inserts_when_assignedSpots_is_below_capacity() {
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(sessionWithCapacity(2, 0)));

            AssignmentOutcome result = useCase.assign(cmd());

            assertThat(result).isEqualTo(AssignmentOutcome.ASSIGNED.INSTANCE);
            verify(assignmentWriter).assertAssignment(SESSION_UUID, STUDENT_UUID);
        }
    }

    @Nested
    @DisplayName("Spec scenario: Capacity invariant trips — Purchase flips to EXCEPTION")
    class CapacityTripReadTime {

        @Test
        void throws_when_assignedSpots_already_equal_capacity_no_insert_attempted() {
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(sessionWithCapacity(1, 1)));

            assertThatThrownBy(() -> useCase.assign(cmd()))
                .isInstanceOf(CapacityBelowAssignedException.class);

            verify(assignmentWriter, never()).assertAssignment(any(UUID.class), any(UUID.class));
        }
    }

    @Nested
    @DisplayName("Spec scenario: UNIQUE race on (sessionId, studentId) routes to EXCEPTION")
    class UniqueRace {

        @Test
        void rethrows_DataIntegrityViolation_as_CapacityBelowAssigned() {
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(sessionWithCapacity(3, 0)));
            org.mockito.Mockito.doThrow(new DataIntegrityViolationException(
                "Duplicate entry for key 'uq_physical_assignment_session_student'"))
                .when(assignmentWriter).assertAssignment(SESSION_UUID, STUDENT_UUID);

            assertThatThrownBy(() -> useCase.assign(cmd()))
                .isInstanceOf(CapacityBelowAssignedException.class);
        }
    }

    @Nested
    @DisplayName("Spec scenario: HOLD_EXPIRED / TARGET_NOT_SCHEDULED residual")
    class SessionAbsent {

        @Test
        void throws_when_session_cannot_be_loaded() {
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.assign(cmd()))
                .isInstanceOf(SessionNotFoundException.class);
            verify(assignmentWriter, never()).assertAssignment(any(UUID.class), any(UUID.class));
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
        private static final SessionId SESSION_ID_2 = SessionId.of(SESSION_UUID_2);
        private static final SessionId SESSION_ID_3 = SessionId.of(SESSION_UUID_3);

        private static SessionClaim claim(UUID sessionId, long secondsFromEpoch) {
            return new SessionClaim(sessionId, Instant.ofEpochSecond(secondsFromEpoch));
        }

        private static MultiSessionCapacityAssignmentCommand multiCmd(List<SessionClaim> claims) {
            return new MultiSessionCapacityAssignmentCommand(claims, STUDENT_UUID, PAYMENT_UUID);
        }

        @Test
        @DisplayName("Claims are attempted in list order and all three are assigned")
        void claims_are_attempted_in_list_order() {
            when(sessionRepository.findById(any(SessionId.class)))
                .thenReturn(Optional.of(sessionWithCapacity(2, 0)));

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
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(sessionWithCapacity(2, 0)));
            when(sessionRepository.findById(SESSION_ID_2)).thenReturn(Optional.of(sessionWithCapacity(1, 1)));

            List<SessionClaim> claims = List.of(
                claim(SESSION_UUID, 1), claim(SESSION_UUID_2, 2), claim(SESSION_UUID_3, 3)
            );

            assertThatThrownBy(() -> useCase.assignAll(multiCmd(claims)))
                .isInstanceOf(CapacityBelowAssignedException.class);

            verify(assignmentWriter).assertAssignment(SESSION_UUID, STUDENT_UUID);
            verify(assignmentWriter, never()).assertAssignment(eq(SESSION_UUID_3), any(UUID.class));
            verify(sessionRepository, never()).findById(SESSION_ID_3);
        }

        @Test
        @DisplayName("N=1 through assignAll matches today's assign behavior")
        void n_equal_one_through_assign_all_matches_assign_behavior() {
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(sessionWithCapacity(2, 0)));

            CapacityAssignments result = useCase.assignAll(multiCmd(List.of(claim(SESSION_UUID, 1))));

            assertThat(result.assignedSessionIds()).containsExactly(SESSION_UUID);
            verify(assignmentWriter).assertAssignment(SESSION_UUID, STUDENT_UUID);
        }
    }
}
