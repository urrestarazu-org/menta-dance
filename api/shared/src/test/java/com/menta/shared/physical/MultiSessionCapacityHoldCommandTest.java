package com.menta.shared.physical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * RED-GREEN: {@link MultiSessionCapacityHoldCommand} is the sibling of
 * {@link MultiSessionCapacityAssignmentCommand} without {@code studentId}
 * (design B2/D5) — a hold reserves a spot, not a seat for a person — sharing
 * the same {@link SessionClaimOrdering} enforcement point.
 */
class MultiSessionCapacityHoldCommandTest {

    private static final UUID SESSION_1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SESSION_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PAYMENT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private static final Instant T1 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-08T10:00:00Z");

    @Test
    void builds_a_command_with_claims_already_in_ascending_order() {
        List<SessionClaim> claims = List.of(
            new SessionClaim(SESSION_1, T1),
            new SessionClaim(SESSION_2, T2)
        );

        MultiSessionCapacityHoldCommand command = new MultiSessionCapacityHoldCommand(claims, PAYMENT_ID);

        assertThat(command.claims()).containsExactly(
            new SessionClaim(SESSION_1, T1),
            new SessionClaim(SESSION_2, T2)
        );
        assertThat(command.paymentId()).isEqualTo(PAYMENT_ID);
    }

    @Test
    void rejects_claims_out_of_scheduled_at_order() {
        List<SessionClaim> outOfOrder = List.of(
            new SessionClaim(SESSION_1, T2),
            new SessionClaim(SESSION_2, T1)
        );

        assertThatThrownBy(() -> new MultiSessionCapacityHoldCommand(outOfOrder, PAYMENT_ID))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_duplicate_session_id() {
        List<SessionClaim> duplicate = List.of(
            new SessionClaim(SESSION_1, T1),
            new SessionClaim(SESSION_1, T2)
        );

        assertThatThrownBy(() -> new MultiSessionCapacityHoldCommand(duplicate, PAYMENT_ID))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_an_empty_claim_list() {
        assertThatThrownBy(() -> new MultiSessionCapacityHoldCommand(List.of(), PAYMENT_ID))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_null_claims() {
        assertThatThrownBy(() -> new MultiSessionCapacityHoldCommand(null, PAYMENT_ID))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejects_null_payment_id() {
        List<SessionClaim> claims = List.of(new SessionClaim(SESSION_1, T1));

        assertThatThrownBy(() -> new MultiSessionCapacityHoldCommand(claims, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void exposed_claim_list_is_immutable() {
        List<SessionClaim> claims = List.of(new SessionClaim(SESSION_1, T1));

        MultiSessionCapacityHoldCommand command = new MultiSessionCapacityHoldCommand(claims, PAYMENT_ID);

        assertThatThrownBy(() -> command.claims().add(new SessionClaim(SESSION_2, T2)))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void has_no_student_id_component() {
        // A hold reserves a spot, not a seat for a person (design B2/D5):
        // there is no studentId accessor to call at all.
        assertThat(MultiSessionCapacityHoldCommand.class.getRecordComponents()).hasSize(2);
    }
}
