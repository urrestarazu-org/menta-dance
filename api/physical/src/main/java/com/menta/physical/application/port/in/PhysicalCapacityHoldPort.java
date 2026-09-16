package com.menta.physical.application.port.in;

import com.menta.physical.application.usecase.CapacityHolds;
import com.menta.physical.application.usecase.ConvertOutcome;
import com.menta.shared.physical.MultiSessionCapacityHoldCommand;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Entry port Physical exposes for the hold write path (#208,
 * US-PHYSICAL-004b, design step 3). Bridged into by {@code api:app}'s
 * {@code PhysicalCapacityHoldAdapter} (design D4, Phase 5), which itself
 * implements {@code api:billing}'s out port for the checkout flow, and
 * (from design step 8) called directly by {@code api:app}'s
 * {@code PhysicalCapacityAssignmentOutboxEventHandler} for conversion.
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

    /**
     * Converts the hold identified by {@code paymentId} into real capacity
     * assignments for {@code studentId} (design step 8, B3). One
     * {@code REQUIRES_NEW} transaction, per session in the hold's own claim
     * order.
     *
     * @return {@link ConvertOutcome.HoldNotFound} when the payment never had
     *     a hold, {@link ConvertOutcome.AlreadyConverted} on a redelivered,
     *     already-converted hold, or {@link ConvertOutcome.Converted} with
     *     the held session ids on the first successful conversion.
     * @throws com.menta.physical.domain.exception.CapacityBelowAssignedException
     *     on the first session that trips the (now hold-excluding) assignment
     *     invariant OR a {@code uq_physical_assignment_session_student} row
     *     collision; every earlier write in this call is rolled back with it.
     */
    ConvertOutcome convertAll(UUID paymentId, UUID studentId);

    /**
     * The session ids currently held for {@code paymentId}, in claim order
     * — empty when no hold exists (design step 8). Used by {@code api:app}'s
     * outbox handler to build the {@code Purchase} row's audit session set
     * BEFORE attempting {@link #convertAll}, so a capacity trip during
     * conversion still has a row to flip to {@code EXCEPTION} — the same
     * discipline the legacy plan+assignAll path already has, where {@code
     * createPurchaseFromPaymentEvent} always runs before the capacity claim.
     */
    List<UUID> heldSessionIds(UUID paymentId);
}
