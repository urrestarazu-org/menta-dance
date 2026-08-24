package com.menta.physical.application.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * Write-side companion to the public read-only
 * {@link PhysicalCapacityAssignmentRepository}. Internal application-layer
 * port: only {@code AssignCapacityUseCase} consumes it; never appears in
 * an IN-port signature.
 */
public interface PhysicalCapacityAssignmentWriter {

    /**
     * @return the {@link Instant} the row was persisted (use the clock,
     *     not {@link Instant#now()}, for testability).
     */
    Instant assertAssignment(UUID sessionId, UUID studentId);
}
