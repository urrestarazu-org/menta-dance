package com.menta.billing.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown by {@code GET /billing/physical/courses/{courseId}/pricing} when
 * the course exists but no pricing has ever been published for it
 * (US-BILLING-009) — distinct from {@link PhysicalCourseNotFoundException},
 * which means the course itself is unknown to Physical.
 */
public class PhysicalCoursePricingNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "PHYSICAL_COURSE_PRICING_NOT_FOUND";

    public PhysicalCoursePricingNotFoundException() {
        super(ERROR_CODE, "No pricing has been published for this course");
    }
}
