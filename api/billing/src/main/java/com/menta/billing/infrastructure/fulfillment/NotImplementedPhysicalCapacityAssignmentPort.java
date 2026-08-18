package com.menta.billing.infrastructure.fulfillment;

import com.menta.billing.application.port.out.PhysicalCapacityAssignmentPort;
import org.springframework.stereotype.Component;

/**
 * Compile-boundary placeholder for {@link PhysicalCapacityAssignmentPort} —
 * mirrors {@code NotImplementedCourseCatalogPort}'s role for #29.
 *
 * // TODO(future issue): replace with an adapter that calls Physical's real
 * write-side capacity port once it exists — Physical only exposes
 * read-only {@code PhysicalCourseAvailabilityPort} today (#40).
 *
 * <p>Callers must not let this propagate as a request failure — {@code
 * PaymentVerificationService} catches any failure from this port and
 * degrades the {@code Purchase} to {@code EXCEPTION} instead, exactly the
 * outcome docs/06-BILLING-API.md already anticipates for a failed
 * assignment.</p>
 */
@Component
public class NotImplementedPhysicalCapacityAssignmentPort implements PhysicalCapacityAssignmentPort {

    private static final String MESSAGE =
        "PhysicalCapacityAssignmentPort adapter not implemented yet — Physical has no write-side port";

    @Override
    public void assign(String physicalSessionId, String purchaseId) {
        throw new UnsupportedOperationException(MESSAGE);
    }
}
