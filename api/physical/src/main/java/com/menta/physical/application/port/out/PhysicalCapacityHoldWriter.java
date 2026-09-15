package com.menta.physical.application.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * Write-side port for the {@code physical_capacity_holds} table (#208,
 * US-PHYSICAL-004b, design B4) — the sibling of
 * {@link PhysicalCapacityAssignmentWriter} for holds. Internal
 * application-layer port: only {@code CreateCapacityHoldUseCase},
 * {@code ReleaseCapacityHoldUseCase}, and (from design step 8)
 * {@code ConvertCapacityHoldUseCase} consume it; never appears in an
 * IN-port signature.
 */
public interface PhysicalCapacityHoldWriter {

    /**
     * Decides the hold invariant {@code assigned + activeHolds + 1 >
     * capacity} from three locking reads (design B4), then inserts a hold
     * row and flushes immediately.
     *
     * @return the {@link Instant} the row was persisted (use the clock, not
     *     {@link Instant#now()}, for testability).
     * @throws com.menta.physical.domain.exception.SessionNotFoundException
     *     when the session does not exist.
     * @throws com.menta.physical.domain.exception.CapacityBelowAssignedException
     *     when the invariant would be violated — nothing is written.
     */
    Instant assertHold(UUID sessionId, UUID paymentId, Instant expiresAt);

    /**
     * Marks the hold row for {@code (paymentId, sessionId)} as converted
     * (design B3): the row stays for idempotency/observability instead of
     * being deleted, and this exclusion is what keeps the arithmetic
     * balanced when the caller immediately calls
     * {@link PhysicalCapacityAssignmentWriter#assertAssignment} in the same
     * transaction.
     */
    void markConverted(UUID paymentId, UUID sessionId);

    /**
     * Deletes every hold row for {@code paymentId}, whatever their state.
     * A no-op when no row exists for the payment.
     */
    void release(UUID paymentId);
}
