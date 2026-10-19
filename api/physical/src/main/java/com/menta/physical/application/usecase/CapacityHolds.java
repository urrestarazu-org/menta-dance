package com.menta.physical.application.usecase;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Typed success result of {@link CreateCapacityHoldUseCase#holdAll} (#208,
 * US-PHYSICAL-004b) — every session held, in the same order the caller
 * supplied them in {@code MultiSessionCapacityHoldCommand.claims()}.
 *
 * <p>The hold-side twin of {@link CapacityAssignments}; same placement
 * rationale — only {@code api:app}'s bridge adapter consumes it.</p>
 *
 * @param heldSessionIds every session id held, in claim order.
 */
public record CapacityHolds(List<UUID> heldSessionIds) {

    public CapacityHolds {
        Objects.requireNonNull(heldSessionIds, "heldSessionIds cannot be null");
        heldSessionIds = List.copyOf(heldSessionIds);
    }
}
