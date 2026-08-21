package com.menta.billing.application.port.in;

import com.menta.billing.application.dto.PhysicalCoursePricingResult;
import com.menta.billing.application.dto.UpdatePhysicalCoursePricingCommand;
import java.util.UUID;

/**
 * Application-layer entry point for publishing a physical course's pricing
 * (US-BILLING-009 escenario 1). Restricted to the course's owning professor
 * or an ADMIN.
 */
public interface UpdatePhysicalCoursePricingUseCase {

    /**
     * @throws com.menta.billing.domain.exception.PhysicalCourseNotFoundException if the course does not exist.
     * @throws com.menta.billing.domain.exception.PricingNotOwnedException if the caller does not own the course.
     * @throws com.menta.billing.domain.exception.InvalidIndividualSurchargeException if the surcharge is <= 0.
     */
    PhysicalCoursePricingResult update(
        String courseId, UpdatePhysicalCoursePricingCommand command, UUID actingUserId, boolean actingAsAdmin
    );
}
