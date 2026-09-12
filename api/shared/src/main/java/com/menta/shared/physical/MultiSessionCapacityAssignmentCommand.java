package com.menta.shared.physical;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
 * <p><b>The compact constructor is the single enforcement point of design
 * A2's total claim order.</b> InnoDB locks the
 * {@code uq_physical_assignment_session_student} index entry at INSERT time.
 * Two overlapping purchases claiming an overlapping session set, iterated in
 * different orders by their respective writers, form a lock cycle; InnoDB
 * kills a victim, and that victim can be a buyer who genuinely had room —
 * a spurious {@code EXCEPTION} on an already-settled payment, produced only
 * by iteration order. Requiring every writer's claims to already be sorted
 * by {@code (scheduledAt ASC, sessionId ASC)} — verified here, not merely
 * documented — turns every possible lock cycle into a lock chain: this class
 * of deadlock becomes impossible by construction, not merely rare. Any
 * future writer, including #208's hold path, either sorts correctly or fails
 * loudly at construction; there is no way to build a command that violates
 * the order.</p>
 *
 * <p>{@code scheduledAt} is the primary key because it is the exact order
 * coverage is already defined in ("the next N scheduled sessions" — design
 * A4): the claim order falls out of computing coverage, with no second sort
 * that could drift out of sync with the first. {@code sessionId} is the
 * tiebreaker because two sessions of the same course can share a
 * {@code scheduledAt}, so {@code scheduledAt} alone is not a total order.</p>
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

    private static final Comparator<SessionClaim> CLAIM_ORDER = Comparator
        .comparing(SessionClaim::scheduledAt)
        .thenComparing(SessionClaim::sessionId);

    public MultiSessionCapacityAssignmentCommand {
        Objects.requireNonNull(claims, "claims cannot be null");
        Objects.requireNonNull(studentId, "studentId cannot be null");
        Objects.requireNonNull(paymentId, "paymentId cannot be null");

        claims = List.copyOf(claims);

        if (claims.isEmpty()) {
            throw new IllegalArgumentException("claims cannot be empty");
        }

        for (int i = 1; i < claims.size(); i++) {
            SessionClaim previous = claims.get(i - 1);
            SessionClaim current = claims.get(i);

            if (CLAIM_ORDER.compare(previous, current) >= 0) {
                throw new IllegalArgumentException(
                    "claims must be strictly ascending by (scheduledAt, sessionId); "
                        + "found " + previous + " before " + current
                );
            }
        }

        // Uniqueness is checked across the whole set, not between neighbours:
        // ordering by (scheduledAt, sessionId) puts a session repeated at two
        // different scheduledAt values far apart, so a pairwise scan never sees
        // the two occurrences together. Left to the database, the repeat hits
        // uq_physical_assignment_session_student mid-claim and rolls the whole
        // all-or-nothing set back — a spurious EXCEPTION on a settled payment,
        // which is precisely what this constructor exists to prevent.
        Set<UUID> distinctSessionIds = claims.stream().map(SessionClaim::sessionId).collect(Collectors.toSet());
        if (distinctSessionIds.size() != claims.size()) {
            throw new IllegalArgumentException("claims cannot contain a duplicate sessionId: " + claims);
        }
    }

    /**
     * One session claimed within a {@link MultiSessionCapacityAssignmentCommand}.
     *
     * @param sessionId the physical session being claimed.
     * @param scheduledAt the session's scheduled start, the primary key of
     *     design A2's total claim order.
     */
    public record SessionClaim(UUID sessionId, Instant scheduledAt) {

        public SessionClaim {
            Objects.requireNonNull(sessionId, "sessionId cannot be null");
            Objects.requireNonNull(scheduledAt, "scheduledAt cannot be null");
        }
    }
}
