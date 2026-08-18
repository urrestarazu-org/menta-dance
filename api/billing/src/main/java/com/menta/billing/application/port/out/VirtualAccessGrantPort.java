package com.menta.billing.application.port.out;

/**
 * Cross-module write port toward Virtual's access grant (US-BILLING-002 —
 * "habilitar acceso... vía puertos/eventos internos"). Same placeholder
 * situation as {@link PhysicalCapacityAssignmentPort} — see its Javadoc.
 */
public interface VirtualAccessGrantPort {

    /** @throws RuntimeException if access cannot be granted — caller treats the Subscription as EXCEPTION. */
    void grant(String virtualCourseId, String subscriptionId);
}
