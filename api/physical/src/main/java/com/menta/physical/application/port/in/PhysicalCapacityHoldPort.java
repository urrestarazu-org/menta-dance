package com.menta.physical.application.port.in;

import com.menta.physical.application.usecase.CapacityHolds;
import com.menta.shared.physical.MultiSessionCapacityHoldCommand;
import java.time.Instant;
import java.util.UUID;

/**
 * Entry port Physical exposes for the hold write path (#208,
 * US-PHYSICAL-004b, design step 3). Bridged into by {@code api:app}'s
 * {@code PhysicalCapacityHoldAdapter} (design D4, Phase 5), which itself
 * implements {@code api:billing}'s out port for the checkout flow.
 *
 * <p>{@code convertAll} is intentionally NOT declared here yet — it is
 * design step 8 (conversion), added once {@code ConvertCapacityHoldUseCase}
 * exists. This phase covers claim creation and release only.</p>
 */
public interface PhysicalCapacityHoldPort {

    /**
     * Ordered, all-or-nothing hold over every session in
     * {@code command.claims()}, in that exact order, under one transaction.
     * A failure on any claim aborts the entire set — no partial subset is
     * ever persisted, mirroring
     * {@link PhysicalCapacityAssignmentPort#assignAll}.
     *
     * @param expiresAt the hold's expiry, shared by every claim in this set
     *     — computed by the caller from the configured TTL (design B5,
     *     Phase 9), never by Physical itself.
     * @return the held session ids, in claim order, when every claim
     *     succeeded.
     * @throws com.menta.physical.domain.exception.CapacityBelowAssignedException
     *     on the first claim that trips the hold invariant OR a
     *     {@code uq_physical_holds_payment_session} row collision; every
     *     earlier insert in this call is rolled back with it.
     */
    CapacityHolds holdAll(MultiSessionCapacityHoldCommand command, Instant expiresAt);

    /**
     * Releases every hold row for {@code paymentId}, whatever their state.
     * A no-op when {@code paymentId} has no hold rows.
     */
    void release(UUID paymentId);
}
