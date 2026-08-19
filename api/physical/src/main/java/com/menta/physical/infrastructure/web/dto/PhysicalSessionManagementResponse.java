package com.menta.physical.infrastructure.web.dto;

import com.menta.physical.application.dto.PhysicalSessionManagementResult;

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
