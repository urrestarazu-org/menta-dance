package com.menta.billing.infrastructure.fulfillment;

import com.menta.billing.application.port.out.VirtualAccessGrantPort;
import org.springframework.stereotype.Component;

/** Compile-boundary placeholder for {@link VirtualAccessGrantPort} — see {@code NotImplementedPhysicalCapacityAssignmentPort}'s Javadoc. */
@Component
public class NotImplementedVirtualAccessGrantPort implements VirtualAccessGrantPort {

    private static final String MESSAGE =
        "VirtualAccessGrantPort adapter not implemented yet — Virtual has no write-side port";

    @Override
    public void grant(String virtualCourseId, String subscriptionId) {
        throw new UnsupportedOperationException(MESSAGE);
    }
}
