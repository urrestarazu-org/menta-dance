package com.menta.physical.application.dto;

public record PhysicalSessionManagementResult(
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
}
