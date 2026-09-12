package com.menta.physical.application.usecase;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Typed success result of {@link AssignCapacityUseCase#assignAll} (design
 * A3, A5): every session claimed, in the same order the caller supplied
 * them in {@code MultiSessionCapacityAssignmentCommand.claims()}.
 *
 * <p>Stays in Physical's application layer rather than promoted to {@code
 * shared}, following {@link AssignmentOutcome}'s existing placement — only
 * {@code api:app}'s adapter consumes it, and Physical still names no
 * {@code Payment}/{@code Purchase} type.</p>
 *
 * @param assignedSessionIds every session id assigned, in claim order.
 */
public record CapacityAssignments(List<UUID> assignedSessionIds) {

    public CapacityAssignments {
        Objects.requireNonNull(assignedSessionIds, "assignedSessionIds cannot be null");
        assignedSessionIds = List.copyOf(assignedSessionIds);
    }
}
