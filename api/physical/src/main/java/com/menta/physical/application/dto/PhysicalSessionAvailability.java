package com.menta.physical.application.dto;

/**
 * Cross-module read shape for a scheduled session's availability — plain
 * types only. {@code activeCapacityHolds} is internal state exposed for
 * transparency, never an action the caller can trigger through this port.
 */
public record PhysicalSessionAvailability(
    String sessionId,
    String courseId,
    String scheduledAt,
    int capacity,
    int assignedSpots,
    int activeCapacityHolds,
    int availableSpots
) {
}
