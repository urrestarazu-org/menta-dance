package com.menta.shared.physical;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One session claimed by an ordered, all-or-nothing capacity writer —
 * {@link MultiSessionCapacityAssignmentCommand} and design #208's
 * {@code MultiSessionCapacityHoldCommand} both share this shape (design B2).
 *
 * <p>Promoted from a nested record of {@code MultiSessionCapacityAssignmentCommand}
 * to a top-level type so both commands enforce {@link SessionClaimOrdering}'s
 * single total-order check rather than each carrying its own copy.</p>
 *
 * @param sessionId the physical session being claimed.
 * @param scheduledAt the session's scheduled start, the primary key of the
 *     total claim order.
 */
public record SessionClaim(UUID sessionId, Instant scheduledAt) {

    public SessionClaim {
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        Objects.requireNonNull(scheduledAt, "scheduledAt cannot be null");
    }
}
