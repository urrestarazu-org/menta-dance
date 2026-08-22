package com.menta.billing.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Cross-module read port toward Physical's course ownership (US-BILLING-009
 * — "Billing valida ownership del profesor vía api:app").
 *
 * <p>Mirrors Physical's own entry port, {@code
 * com.menta.physical.application.port.in.PhysicalCourseOwnershipPort}: {@code
 * api:app} implements this billing out port by calling that Physical in
 * port directly — a plain Java interface, never HTTP, RabbitMQ or a shared
 * schema, same pattern as {@code CatalogCompositionService}. Deliberately
 * NOT the {@code NotImplementedXxxPort} placeholder pattern used elsewhere
 * in this module (e.g. {@code NotImplementedCourseCatalogPort}): those never
 * got a real adapter across two prior issues, so this port is wired for real
 * from the start.</p>
 */
public interface PhysicalCourseOwnershipPort {

    /**
     * @param courseId the opaque course id to look up.
     * @return the owning professor's id, or {@code Optional.empty()} if the
     *     course does not exist in Physical.
     */
    Optional<UUID> findProfessorId(String courseId);
}
