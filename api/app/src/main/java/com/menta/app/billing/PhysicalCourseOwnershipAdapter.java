package com.menta.app.billing;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implements Billing's {@code
 * com.menta.billing.application.port.out.PhysicalCourseOwnershipPort} out
 * port by calling Physical's entry port directly (US-BILLING-009) — same
 * cross-module composition pattern as {@code CatalogCompositionService}: a
 * plain Java call inside {@code api:app}, never HTTP, RabbitMQ or a shared
 * schema (ADR-0037).
 *
 * <p>Both modules expose a type named {@code PhysicalCourseOwnershipPort}
 * (Billing's out port and Physical's in port) — fully qualified here since
 * they cannot both be imported under the same simple name.</p>
 */
@Component
public class PhysicalCourseOwnershipAdapter
    implements com.menta.billing.application.port.out.PhysicalCourseOwnershipPort {

    private final com.menta.physical.application.port.in.PhysicalCourseOwnershipPort physicalCourseOwnershipPort;

    public PhysicalCourseOwnershipAdapter(
        com.menta.physical.application.port.in.PhysicalCourseOwnershipPort physicalCourseOwnershipPort
    ) {
        this.physicalCourseOwnershipPort = physicalCourseOwnershipPort;
    }

    @Override
    public Optional<UUID> findProfessorId(String courseId) {
        return physicalCourseOwnershipPort.findProfessorId(courseId);
    }
}
