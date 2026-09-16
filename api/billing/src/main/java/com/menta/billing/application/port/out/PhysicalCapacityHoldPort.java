package com.menta.billing.application.port.out;

import com.menta.billing.domain.exception.PhysicalCapacityUnavailableException;
import com.menta.shared.physical.MultiSessionCapacityHoldCommand;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cross-module write port toward Physical's technical capacity hold (#208,
 * US-PHYSICAL-004b, design D4) — {@code api:app} implements this by calling
 * Physical's entry port {@code
 * com.menta.physical.application.port.in.PhysicalCapacityHoldPort} directly,
 * same composition pattern as {@link PhysicalCourseAvailabilityPort}: a plain
 * Java call, never HTTP, RabbitMQ or a shared schema (ADR-0037).
 *
 * <p>{@code command} is {@code com.menta.shared.physical
 * .MultiSessionCapacityHoldCommand}, already reused directly from {@code
 * api:shared} rather than duplicated as a billing-local DTO — {@code
 * api:billing} already depends on {@code api:shared} (see {@code
 * CoveragePlanner}'s own use of shared physical types), and the command
 * itself carries no {@code com.menta.physical.*} type, so reusing it here
 * does not weaken the ArchUnit boundary that keeps {@code
 * CreatePhysicalPurchaseCheckoutUseCaseImpl} free of {@code
 * com.menta.physical..} dependencies.</p>
 *
 * <p>Physical's in-port returns {@code
 * com.menta.physical.application.usecase.CapacityHolds}, a physical-owned
 * type this port must not leak — the adapter unwraps it to {@code
 * List<UUID>} of held session ids, in claim order.</p>
 */
public interface PhysicalCapacityHoldPort {

    /**
     * Ordered, all-or-nothing hold over every session in {@code
     * command.claims()}, in that exact order, under one transaction —
     * mirrors {@code
     * com.menta.physical.application.port.in.PhysicalCapacityHoldPort
     * #holdAll}, which this port's adapter calls directly.
     *
     * @param command the ordered claim set and the correlating {@code
     *     paymentId} (already {@link MultiSessionCapacityHoldCommand}'s
     *     shape, {@code api:shared}).
     * @param expiresAt the hold's expiry, shared by every claim in this
     *     set — computed by the caller (billing's checkout use case) from
     *     the configured TTL, never by Physical itself.
     * @return the held session ids, in claim order, when every claim
     *     succeeded.
     * @throws PhysicalCapacityUnavailableException on the first claim that
     *     trips the hold invariant — the same {@code 409
     *     CAPACITY_UNAVAILABLE} guarantee (design D2), now truthful instead
     *     of best-effort.
     */
    List<UUID> hold(MultiSessionCapacityHoldCommand command, Instant expiresAt);

    /**
     * Releases every hold row for {@code paymentId}, whatever their state.
     * A no-op when {@code paymentId} has no hold rows — mirrors {@code
     * com.menta.physical.application.port.in.PhysicalCapacityHoldPort
     * #release} exactly.
     */
    void release(UUID paymentId);
}
