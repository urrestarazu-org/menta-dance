package com.menta.physical.application.port.out;

import com.menta.shared.physical.AssignmentType;
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
     * Records one confirmed assignment of a student to a session under the capacity invariant.
     *
     * <p>The write is refused with {@code CapacityBelowAssignedException} when assigned spots plus
     * active holds plus this assignment would exceed the session capacity, and a duplicate
     * (session, student) pair is refused with {@code StudentAlreadyAssignedException}: the student
     * already holds the session, whatever the remaining capacity.
     *
     * @param type how the seat was acquired; persisted verbatim on the assignment row.
     * @return the {@link Instant} the row was persisted (use the clock,
     *     not {@link Instant#now()}, for testability).
     */
    Instant assertAssignment(UUID sessionId, UUID studentId, AssignmentType type);
}
