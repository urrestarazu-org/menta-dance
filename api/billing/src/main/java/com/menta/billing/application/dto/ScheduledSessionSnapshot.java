package com.menta.billing.application.dto;

import java.time.Instant;

/**
 * Billing's own cross-module read shape for a scheduled physical session
 * (US-BILLING-006) — deliberately not Physical's {@code
 * PhysicalSessionAvailability}: {@code
 * PhysicalCourseAvailabilityPort} (billing's out port) must not import
 * Physical types, only plain values an {@code api:app} adapter maps into.
 */
public record ScheduledSessionSnapshot(String sessionId, Instant scheduledAt, int availableSpots) {
}
