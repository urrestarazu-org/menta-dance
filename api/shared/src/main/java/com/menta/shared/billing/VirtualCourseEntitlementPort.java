package com.menta.shared.billing;

import java.util.UUID;

/**
 * Read contract through which Virtual obtains Billing's commercial facts for a
 * course (ADR-0039).
 *
 * <p>Billing owns the plan lifecycle and frozen subscription snapshots; Virtual
 * owns the final content-access decision. The returned facts must never expose
 * Billing entities, plans, or persistence details. A {@code null} user means
 * anonymous access: Billing still reports whether the course is planned but
 * always reports no entitlement and does not query subscriptions.</p>
 *
 * <p>A cancelled subscription can remain a current entitlement until its paid
 * {@code endDate}; cancellation stops renewal, it does not forfeit the paid
 * period (US-BILLING-011).</p>
 */
public interface VirtualCourseEntitlementPort {

    /**
     * Resolves whether a course is sold by any active plan and whether the
     * supplied student has a current frozen-snapshot entitlement for it.
     *
     * @param userIdOrNull the authenticated student, or {@code null} for an anonymous caller
     * @param courseId the opaque Virtual course identifier
     * @return only commercial facts; this contract never grants content access itself
     */
    CourseAccessSnapshot resolveCourseAccess(UUID userIdOrNull, String courseId);
}
