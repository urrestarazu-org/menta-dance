package com.menta.physical.application.port.out;

import java.util.Objects;
import java.util.UUID;

/**
 * One hold row for a payment, as read by
 * {@link PhysicalCapacityHoldWriter#findByPaymentIdOrdered} (design step 8) —
 * just enough to decide {@code ConvertOutcome} and drive the per-session
 * conversion loop without leaking the JPA entity across the port boundary.
 *
 * @param sessionId the held session.
 * @param converted whether this row's {@code converted_at} is already set.
 */
public record HeldSessionRow(UUID sessionId, boolean converted) {

    public HeldSessionRow {
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
    }
}
