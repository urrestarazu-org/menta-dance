package com.menta.billing.application.port.out;

/**
 * Cross-module write port toward Physical's capacity assignment
 * (US-BILLING-002 — "asignar cupo... vía puertos/eventos internos").
 *
 * <p>Physical does not expose a write-side port yet — only the read-only
 * {@code PhysicalCourseAvailabilityPort} (#40). Until a future issue builds
 * the real one, the wired adapter is a placeholder — see {@code
 * NotImplementedPhysicalCapacityAssignmentPort} — and every {@code Purchase}
 * this issue creates lands in {@code EXCEPTION}, exactly the fulfillment
 * outcome docs/06-BILLING-API.md already anticipates for a failed
 * assignment. The {@code Payment} itself still reaches {@code Completed}:
 * financial settlement never waits on fulfillment.</p>
 */
public interface PhysicalCapacityAssignmentPort {

    /** @throws RuntimeException if the assignment cannot be made — caller treats the Purchase as EXCEPTION. */
    void assign(String physicalSessionId, String purchaseId);
}
