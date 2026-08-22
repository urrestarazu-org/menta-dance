package com.menta.shared.billing;

import java.util.UUID;

/**
 * Read contract Virtual uses to ask Billing whether a student currently has
 * access to a course. Billing owns the subscription lifecycle and implements
 * this contract; Virtual remains the owner of its content and authorization
 * endpoint (ADR-0039).
 */
public interface VirtualCourseEntitlementPort {

    /** Returns whether an active, unexpired subscription snapshot includes the course. */
    boolean hasActiveEntitlement(UUID userId, String courseId);
}
