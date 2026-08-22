package com.menta.billing.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when {@code PhysicalCourseOwnershipPort} resolves no professor for
 * a {@code courseId} (US-BILLING-009) — the course does not exist in
 * Physical at all, as opposed to existing but not owned by the caller (see
 * {@link PricingNotOwnedException}).
 */
public class PhysicalCourseNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "PHYSICAL_COURSE_NOT_FOUND";

    public PhysicalCourseNotFoundException() {
        super(ERROR_CODE, "Physical course not found");
    }
}
