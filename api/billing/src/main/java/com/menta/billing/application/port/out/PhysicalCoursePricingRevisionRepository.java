package com.menta.billing.application.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit trail for physical course pricing changes
 * (US-BILLING-009: "historial inmutable con profesor, motivo, timestamp y
 * versión"). Calcado de {@code VirtualCourseAuditRepository} (Virtual's
 * equivalent for course/module/lesson management), with {@code reason} and
 * {@code version} added since the US requires both to be queryable. No
 * {@code UPDATE}/{@code DELETE} is ever issued against this table from the
 * application.
 */
public interface PhysicalCoursePricingRevisionRepository {

    /**
     * @param previousValue a human-readable snapshot before the change, or
     *     {@code null} for the first-ever version (there is no "before").
     * @param newValue a human-readable snapshot after the change.
     */
    void append(
        String courseId, UUID actorId, String reason, int version, String previousValue, String newValue,
        Instant occurredAt
    );
}
