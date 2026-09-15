package com.menta.shared.physical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * RED-GREEN: the single enforcement point of design B2's total claim order,
 * extracted from {@link MultiSessionCapacityAssignmentCommand} so every
 * present and future ordered writer — including this design's own hold
 * command — shares exactly one place the {@code (scheduledAt ASC, sessionId
 * ASC)} order is verified.
 *
 * <p>Uniqueness is checked across the <b>whole</b> claim list, not merely
 * between adjacent pairs: sorted by {@code (scheduledAt, sessionId)}, a
 * session repeated at a different {@code scheduledAt} lands with other
 * claims between its two occurrences, so a pairwise scan never sees them
 * together (#41 PR2 review).</p>
 */
class SessionClaimOrderingTest {

    private static final UUID SESSION_1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SESSION_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SESSION_3 = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static final Instant T1 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-08T10:00:00Z");
    private static final Instant T3 = Instant.parse("2026-01-15T10:00:00Z");

    @Test
    void accepts_claims_already_in_ascending_order() {
        List<SessionClaim> claims = List.of(
            new SessionClaim(SESSION_1, T1),
            new SessionClaim(SESSION_2, T2),
            new SessionClaim(SESSION_3, T3)
        );

        List<SessionClaim> ordered = SessionClaimOrdering.requireTotalOrder(claims);

        assertThat(ordered).containsExactly(
            new SessionClaim(SESSION_1, T1),
            new SessionClaim(SESSION_2, T2),
            new SessionClaim(SESSION_3, T3)
        );
    }

    @Test
    void breaks_ties_on_session_id_when_scheduled_at_is_equal() {
        UUID lower = UUID.fromString("10000000-0000-0000-0000-000000000000");
        UUID higher = UUID.fromString("20000000-0000-0000-0000-000000000000");

        List<SessionClaim> ascendingBySessionId = List.of(
            new SessionClaim(lower, T1),
            new SessionClaim(higher, T1)
        );

        List<SessionClaim> ordered = SessionClaimOrdering.requireTotalOrder(ascendingBySessionId);

        assertThat(ordered).containsExactly(
            new SessionClaim(lower, T1),
            new SessionClaim(higher, T1)
        );

        List<SessionClaim> descendingBySessionId = List.of(
            new SessionClaim(higher, T1),
            new SessionClaim(lower, T1)
        );

        assertThatThrownBy(() -> SessionClaimOrdering.requireTotalOrder(descendingBySessionId))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_claims_out_of_scheduled_at_order() {
        List<SessionClaim> outOfOrder = List.of(
            new SessionClaim(SESSION_1, T2),
            new SessionClaim(SESSION_2, T1)
        );

        assertThatThrownBy(() -> SessionClaimOrdering.requireTotalOrder(outOfOrder))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_duplicate_session_id() {
        List<SessionClaim> duplicate = List.of(
            new SessionClaim(SESSION_1, T1),
            new SessionClaim(SESSION_1, T2)
        );

        assertThatThrownBy(() -> SessionClaimOrdering.requireTotalOrder(duplicate))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * The duplicate above is adjacent, which a pairwise order scan catches
     * incidentally. This one is not: sorted by {@code (scheduledAt,
     * sessionId)}, the repeated session lands with another claim between its
     * two occurrences, so a scan that only compares neighbours never sees
     * them together. This is the exact bug #41 PR2 review fixed in
     * {@link MultiSessionCapacityAssignmentCommand} — it must not resurface
     * in the shared enforcement point.
     */
    @Test
    void rejects_a_duplicate_session_id_that_is_not_adjacent() {
        List<SessionClaim> duplicate = List.of(
            new SessionClaim(SESSION_1, T1),
            new SessionClaim(SESSION_2, T2),
            new SessionClaim(SESSION_1, T3)
        );

        assertThatThrownBy(() -> SessionClaimOrdering.requireTotalOrder(duplicate))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_an_empty_claim_list() {
        assertThatThrownBy(() -> SessionClaimOrdering.requireTotalOrder(List.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_null_claims() {
        assertThatThrownBy(() -> SessionClaimOrdering.requireTotalOrder(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void returned_claim_list_is_immutable() {
        List<SessionClaim> claims = List.of(new SessionClaim(SESSION_1, T1));

        List<SessionClaim> ordered = SessionClaimOrdering.requireTotalOrder(claims);

        assertThatThrownBy(() -> ordered.add(new SessionClaim(SESSION_2, T2)))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
