package com.menta.billing.application.port.in;

import com.menta.billing.application.dto.PhysicalCoursePricingResult;

/**
 * Application-layer entry point for reading a physical course's current
 * pricing (US-BILLING-009). Public and cacheable — no ownership check.
 */
public interface GetPhysicalCoursePricingUseCase {

    /** @throws com.menta.billing.domain.exception.PhysicalCoursePricingNotFoundException if never published. */
    PhysicalCoursePricingResult getPricing(String courseId);
}
