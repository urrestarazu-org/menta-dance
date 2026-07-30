package com.menta.shared.outbox;

import java.time.Instant;

/**
 * Marker for cross-module outbox events.
 *
 * Pure Java record living in :api:shared with no Spring or JPA imports (ADR-0021).
 * The persistence model and reconciler-side projections live in :api:app's
 * infrastructure layer; this record is the contract consumers should depend on.
 *
 * Fields:
 * - eventId: ULID-style opaque string (26 chars). Globally unique per producer.
 * - eventType: dotted path, e.g. "auth.AuthUserLoggedIn".
 * - aggregateId: domain aggregate root identifier (jti UUID or familyId hash, etc).
 * - payload: JSON-serialized event body.
 * - status: lifecycle state.
 * - createdAt: commit timestamp.
 */
public record OutboxEvent(
    String eventId,
    String eventType,
    String aggregateId,
    String payload,
    OutboxStatus status,
    Instant createdAt
) {
}
