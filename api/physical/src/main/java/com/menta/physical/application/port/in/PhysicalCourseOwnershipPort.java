package com.menta.physical.application.port.in;

import java.util.Optional;
import java.util.UUID;

/**
 * Entry point Physical exposes so other modules can resolve a course's owner
 * for their own authorization decisions (US-BILLING-009). {@code api:app}'s
 * billing-pricing composition calls this directly — same cross-module
 * pattern as {@code PhysicalCourseAvailabilityPort} and {@code
 * docs/27-CLEAN-ARCHITECTURE-GUIDE.md}'s {@code UserQueryPort} example: a
 * Java interface, never HTTP, RabbitMQ or a shared schema.
 */
public interface PhysicalCourseOwnershipPort {

    /**
     * Unfiltered by status — unlike the public catalog's {@code
     * findActiveById}, an owner must be able to price an {@code INACTIVE}
     * course too.
     *
     * @param courseId the opaque course id to look up.
     * @return the owning professor's id, or {@code Optional.empty()} if the
     *     course does not exist.
     */
    Optional<UUID> findProfessorId(String courseId);
}
