package com.menta.shared.physical;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The single enforcement point of the total claim order
 * {@code (scheduledAt ASC, sessionId ASC)} shared by every ordered,
 * all-or-nothing capacity writer (design B2, formerly A2).
 *
 * <p>InnoDB locks the relevant unique index entry at INSERT time. Two
 * overlapping claim sets, iterated in different orders by their respective
 * writers, form a lock cycle; InnoDB kills a victim, and that victim can be a
 * buyer who genuinely had room — a spurious {@code EXCEPTION} produced only
 * by iteration order. Requiring every writer's claims to already be sorted by
 * {@code (scheduledAt ASC, sessionId ASC)} — verified here, not merely
 * documented — turns every possible lock cycle into a lock chain: this class
 * of deadlock becomes impossible by construction, not merely rare.</p>
 *
 * <p>{@code scheduledAt} is the primary key because it is the exact order
 * coverage is already defined in. {@code sessionId} is the tiebreaker because
 * two sessions of the same course can share a {@code scheduledAt}, so
 * {@code scheduledAt} alone is not a total order.</p>
 */
public final class SessionClaimOrdering {

    private static final Comparator<SessionClaim> CLAIM_ORDER = Comparator
        .comparing(SessionClaim::scheduledAt)
        .thenComparing(SessionClaim::sessionId);

    private SessionClaimOrdering() {
    }

    /**
     * Verifies {@code claims} is non-empty, strictly ascending by
     * {@code (scheduledAt, sessionId)}, and contains no duplicate
     * {@code sessionId} anywhere in the set — not merely between neighbours.
     *
     * @param claims the claims to verify, must not be {@code null}.
     * @return an immutable copy of {@code claims}, unchanged in order.
     * @throws NullPointerException if {@code claims} is {@code null}.
     * @throws IllegalArgumentException if {@code claims} is empty, not
     *     strictly ascending, or contains a duplicate {@code sessionId}.
     */
    public static List<SessionClaim> requireTotalOrder(List<SessionClaim> claims) {
        Objects.requireNonNull(claims, "claims cannot be null");

        List<SessionClaim> ordered = List.copyOf(claims);

        if (ordered.isEmpty()) {
            throw new IllegalArgumentException("claims cannot be empty");
        }

        for (int i = 1; i < ordered.size(); i++) {
            SessionClaim previous = ordered.get(i - 1);
            SessionClaim current = ordered.get(i);

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
        // the two occurrences together (#41 PR2 review). Left unchecked, the
        // repeat hits the database's uniqueness constraint mid-claim and rolls
        // the whole all-or-nothing set back — a spurious EXCEPTION on an
        // already-settled payment, which is precisely what this method exists
        // to prevent.
        Set<UUID> distinctSessionIds = ordered.stream().map(SessionClaim::sessionId).collect(Collectors.toSet());
        if (distinctSessionIds.size() != ordered.size()) {
            throw new IllegalArgumentException("claims cannot contain a duplicate sessionId: " + ordered);
        }

        return ordered;
    }
}
