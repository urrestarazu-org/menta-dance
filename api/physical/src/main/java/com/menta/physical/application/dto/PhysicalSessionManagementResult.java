package com.menta.physical.application.dto;

/**
 * Management-view projection of a physical session, already rendered as text and primitives.
 *
 * <p>Returned by the session management use cases (create, update, batch create and list) so the
 * web layer never touches the domain {@code PhysicalSession}. {@code sessionId}, {@code courseId}
 * and {@code scheduledAt} are the canonical string forms (UUID and ISO-8601 instant), {@code
 * status} is the {@code SessionStatus} name, and {@code availableSpots} is capacity minus the live
 * assigned and held counts, floored at zero.
 */
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
