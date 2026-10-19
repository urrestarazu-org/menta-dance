package com.menta.physical.infrastructure.web.dto;

import com.menta.physical.application.dto.PhysicalSessionManagementResult;

/**
 * Wire shape of a session in the management endpoints.
 *
 * <p>Mirrors {@code PhysicalSessionManagementResult} field for field; the identifiers and the
 * instant are already text there.
 */
public record PhysicalSessionManagementResponse(
    String sessionId,
    String courseId,
    String scheduledAt,
    int capacity,
    int assignedSpots,
    int activeCapacityHolds,
    int availableSpots,
    String status,
    String notes
) {

    /** Maps the application result to the wire shape without altering any value. */
    public static PhysicalSessionManagementResponse from(PhysicalSessionManagementResult result) {
        return new PhysicalSessionManagementResponse(
            result.sessionId(),
            result.courseId(),
            result.scheduledAt(),
            result.capacity(),
            result.assignedSpots(),
            result.activeCapacityHolds(),
            result.availableSpots(),
            result.status(),
            result.notes()
        );
    }
}
