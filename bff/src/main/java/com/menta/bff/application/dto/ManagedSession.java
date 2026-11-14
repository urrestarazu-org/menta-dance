package com.menta.bff.application.dto;

import java.time.Instant;

/**
 * A session of a physical course as the management API reports it ({@code PhysicalSessionManagementResponse}).
 *
 * @param sessionId           session identifier
 * @param courseId            identifier of the course it belongs to
 * @param scheduledAt         start of the session
 * @param capacity            total spots
 * @param assignedSpots       spots with a confirmed assignment
 * @param activeCapacityHolds spots held by a purchase in progress
 * @param availableSpots      spots still free
 * @param status              {@code SCHEDULED} or {@code CANCELLED}
 * @param notes               free-text notes, may be {@code null}
 */
public record ManagedSession(
        String sessionId, String courseId, Instant scheduledAt, int capacity, int assignedSpots,
        int activeCapacityHolds, int availableSpots, String status, String notes) {
}
