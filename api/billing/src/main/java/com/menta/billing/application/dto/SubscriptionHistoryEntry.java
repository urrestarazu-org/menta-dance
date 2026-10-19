package com.menta.billing.application.dto;

import com.menta.billing.domain.model.SubscriptionStatus;
import java.time.Instant;

/**
 * One row of a user's subscription history (US-BILLING-004, design.md A3).
 *
 * <p>A deliberate projection, not a rehydrated {@link com.menta.billing.domain.model.Subscription}:
 * history renders id/plan/status/dates and never courses, so mapping through {@code
 * toDomainWithCourses} would issue one {@code subscription_courses} query per row for data the
 * caller discards, and would hand back a domain object whose course snapshot was silently
 * falsified to empty. The infrastructure adapter maps {@code SubscriptionJpaEntity} directly into
 * this record — the application layer never sees the JPA entity.</p>
 */
public record SubscriptionHistoryEntry(
    String id, String planId, SubscriptionStatus status, Instant startDate, Instant endDate, Instant createdAt
) {
}
