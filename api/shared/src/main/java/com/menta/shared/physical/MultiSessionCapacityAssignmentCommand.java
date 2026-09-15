package com.menta.shared.physical;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Cross-module record consumed by physical's
 * {@code AssignCapacityUseCase.assignAll(...)} from {@code api:app}'s outbox
 * handler (design A2, A3, A5) — the ordered, all-or-nothing counterpart of
 * {@link CapacityAssignmentCommand}.
 *
 * <p>No JPA / JSON / Spring annotation: this is plain Java shared between
 * modules, exactly like {@link CapacityAssignmentCommand}. Producer is the
 * {@code PhysicalCapacityAssignmentOutboxEventHandler}, which resolves one
 * purchase's eligible sessions — via {@code CoveragePlanner} for
 * {@code MONTHLY}, or the quote's own {@code selectedSessionId} for
 * {@code INDIVIDUAL} — into an ordered list of claims.</p>
 *
 * <p><b>The compact constructor delegates to {@link SessionClaimOrdering}, the
 * single enforcement point of design B2's (formerly A2's) total claim
 * order.</b> InnoDB locks the {@code uq_physical_assignment_session_student}
 * index entry at INSERT time. Two overlapping purchases claiming an
 * overlapping session set, iterated in different orders by their respective
 * writers, form a lock cycle; InnoDB kills a victim, and that victim can be a
 * buyer who genuinely had room — a spurious {@code EXCEPTION} on an
 * already-settled payment, produced only by iteration order. Requiring every
 * writer's claims to already be sorted by {@code (scheduledAt ASC, sessionId
 * ASC)} — verified in {@link SessionClaimOrdering}, not merely documented —
 * turns every possible lock cycle into a lock chain: this class of deadlock
 * becomes impossible by construction, not merely rare. Any future writer,
 * including #208's hold path, either sorts correctly or fails loudly at
 * construction; there is no way to build a command that violates the order.</p>
 *
 * @param claims the sessions to claim, already sorted ascending by
 *     {@code (scheduledAt, sessionId)}, with no duplicate {@code sessionId}.
 * @param studentId the buyer's user id, identical for every claim in the set.
 * @param paymentId the billing payment id this assignment set is a
 *     consequence of.
 */
public record MultiSessionCapacityAssignmentCommand(
    List<SessionClaim> claims,
    UUID studentId,
    UUID paymentId
) {

    public MultiSessionCapacityAssignmentCommand {
        Objects.requireNonNull(studentId, "studentId cannot be null");
        Objects.requireNonNull(paymentId, "paymentId cannot be null");

        claims = SessionClaimOrdering.requireTotalOrder(claims);
    }
}
