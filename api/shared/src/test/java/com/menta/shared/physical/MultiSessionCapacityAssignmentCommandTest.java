package com.menta.shared.physical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.shared.physical.MultiSessionCapacityAssignmentCommand.SessionClaim;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * RED-GREEN: every assertion references
 * {@link MultiSessionCapacityAssignmentCommand}, the ordered neutral contract
 * physical's {@code AssignCapacityUseCase.assignAll(...)} consumes from
 * {@code api:app}'s outbox handler (design A2, A5).
 *
 * <p>This is A2's enforcement point: the compact constructor is the only
 * place the total order `(scheduledAt ASC, sessionId ASC)` is verified,
 * so any writer — including this design's own future writers — either
 * builds a correctly ordered command or fails at construction. There is no
 * convention to violate.</p>
 */
class MultiSessionCapacityAssignmentCommandTest {

    private static final UUID SESSION_1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SESSION_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SESSION_3 = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID STUDENT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID PAYMENT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private static final Instant T1 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-08T10:00:00Z");
    private static final Instant T3 = Instant.parse("2026-01-15T10:00:00Z");

    @Test
    void builds_a_command_with_claims_already_in_ascending_order() {
        List<SessionClaim> claims = List.of(
            new SessionClaim(SESSION_1, T1),
            new SessionClaim(SESSION_2, T2),
            new SessionClaim(SESSION_3, T3)
        );

        MultiSessionCapacityAssignmentCommand command =
            new MultiSessionCapacityAssignmentCommand(claims, STUDENT_ID, PAYMENT_ID);

        assertThat(command.claims()).containsExactly(
            new SessionClaim(SESSION_1, T1),
            new SessionClaim(SESSION_2, T2),
            new SessionClaim(SESSION_3, T3)
        );
        assertThat(command.studentId()).isEqualTo(STUDENT_ID);
        assertThat(command.paymentId()).isEqualTo(PAYMENT_ID);
    }

    @Test
    void rejects_claims_out_of_scheduled_at_order() {
        List<SessionClaim> outOfOrder = List.of(
            new SessionClaim(SESSION_1, T2),
            new SessionClaim(SESSION_2, T1)
        );

        assertThatThrownBy(() -> new MultiSessionCapacityAssignmentCommand(
            outOfOrder, STUDENT_ID, PAYMENT_ID
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void breaks_ties_on_session_id_when_scheduled_at_is_equal() {
        UUID lower = UUID.fromString("10000000-0000-0000-0000-000000000000");
        UUID higher = UUID.fromString("20000000-0000-0000-0000-000000000000");

        List<SessionClaim> ascendingBySessionId = List.of(
            new SessionClaim(lower, T1),
            new SessionClaim(higher, T1)
        );

        MultiSessionCapacityAssignmentCommand command =
            new MultiSessionCapacityAssignmentCommand(ascendingBySessionId, STUDENT_ID, PAYMENT_ID);

        assertThat(command.claims()).containsExactly(
            new SessionClaim(lower, T1),
            new SessionClaim(higher, T1)
        );

        List<SessionClaim> descendingBySessionId = List.of(
            new SessionClaim(higher, T1),
            new SessionClaim(lower, T1)
        );

        assertThatThrownBy(() -> new MultiSessionCapacityAssignmentCommand(
            descendingBySessionId, STUDENT_ID, PAYMENT_ID
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_duplicate_session_id() {
        List<SessionClaim> duplicate = List.of(
            new SessionClaim(SESSION_1, T1),
            new SessionClaim(SESSION_1, T2)
        );

        assertThatThrownBy(() -> new MultiSessionCapacityAssignmentCommand(
            duplicate, STUDENT_ID, PAYMENT_ID
        )).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * The duplicate above is adjacent, which the pairwise order scan catches
     * incidentally. This one is not: sorted by {@code (scheduledAt,
     * sessionId)}, a session repeated at a different {@code scheduledAt} lands
     * with other claims between its two occurrences, so a scan that only
     * compares neighbours never sees them together.
     *
     * <p>It has to fail here rather than at the database. Reaching INSERT, the
     * repeat collides with {@code uq_physical_assignment_session_student} and
     * rolls back the whole all-or-nothing claim — a spurious {@code EXCEPTION}
     * on an already-settled payment, which is the exact failure this class
     * exists to make impossible.</p>
     */
    @Test
    void rejects_a_duplicate_session_id_that_is_not_adjacent() {
        List<SessionClaim> duplicate = List.of(
            new SessionClaim(SESSION_1, T1),
            new SessionClaim(SESSION_2, T2),
            new SessionClaim(SESSION_1, T3)
        );

        assertThatThrownBy(() -> new MultiSessionCapacityAssignmentCommand(
            duplicate, STUDENT_ID, PAYMENT_ID
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_an_empty_claim_list() {
        assertThatThrownBy(() -> new MultiSessionCapacityAssignmentCommand(
            List.of(), STUDENT_ID, PAYMENT_ID
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_null_claims() {
        assertThatThrownBy(() -> new MultiSessionCapacityAssignmentCommand(
            null, STUDENT_ID, PAYMENT_ID
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejects_null_student_id() {
        List<SessionClaim> claims = List.of(new SessionClaim(SESSION_1, T1));

        assertThatThrownBy(() -> new MultiSessionCapacityAssignmentCommand(
            claims, null, PAYMENT_ID
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejects_null_payment_id() {
        List<SessionClaim> claims = List.of(new SessionClaim(SESSION_1, T1));

        assertThatThrownBy(() -> new MultiSessionCapacityAssignmentCommand(
            claims, STUDENT_ID, null
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void exposed_claim_list_is_immutable() {
        List<SessionClaim> claims = List.of(new SessionClaim(SESSION_1, T1));

        MultiSessionCapacityAssignmentCommand command =
            new MultiSessionCapacityAssignmentCommand(claims, STUDENT_ID, PAYMENT_ID);

        assertThatThrownBy(() -> command.claims().add(new SessionClaim(SESSION_2, T2)))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void session_claim_rejects_null_session_id() {
        assertThatThrownBy(() -> new SessionClaim(null, T1))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void session_claim_rejects_null_scheduled_at() {
        assertThatThrownBy(() -> new SessionClaim(SESSION_1, null))
            .isInstanceOf(NullPointerException.class);
    }
}
