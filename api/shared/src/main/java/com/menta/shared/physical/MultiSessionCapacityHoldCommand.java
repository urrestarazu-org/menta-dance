package com.menta.shared.physical;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Cross-module record consumed by physical's {@code CreateCapacityHoldUseCase
 * .holdAll(...)} — the sibling of {@link MultiSessionCapacityAssignmentCommand}
 * without {@code studentId} (design B2, D5).
 *
 * <p>A hold reserves a <b>spot</b>, not a seat for a person: the student
 * identity is only resolvable from a settled {@code Payment}, and this
 * command exists before that payment settles. Persisting a {@code studentId}
 * here would add a column nothing reads, imply a student-visible reservation
 * D5 forbids, and invite a wrongly restrictive uniqueness on re-purchase after
 * a released hold.</p>
 *
 * <p>The compact constructor delegates to {@link SessionClaimOrdering}, the
 * same single enforcement point {@link MultiSessionCapacityAssignmentCommand}
 * uses, so the deadlock-preventing total order (design B2, formerly A2) is
 * never duplicated.</p>
 *
 * @param claims the sessions to claim, already sorted ascending by
 *     {@code (scheduledAt, sessionId)}, with no duplicate {@code sessionId}.
 * @param paymentId the billing payment id this hold set is a consequence of.
 */
public record MultiSessionCapacityHoldCommand(
    List<SessionClaim> claims,
    UUID paymentId
) {

    public MultiSessionCapacityHoldCommand {
        Objects.requireNonNull(paymentId, "paymentId cannot be null");

        claims = SessionClaimOrdering.requireTotalOrder(claims);
    }
}
