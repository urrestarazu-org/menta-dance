package com.menta.billing.application.port.out;

import com.menta.billing.domain.model.PhysicalCoursePricing;
import java.util.Optional;

/**
 * Persistence port for a physical course's current {@link
 * PhysicalCoursePricing} (US-BILLING-009). Holds only the current version —
 * the full version history lives in the append-only {@link
 * PhysicalCoursePricingRevisionRepository}.
 */
public interface PhysicalCoursePricingRepository {

    /** Finds the current pricing version of a course, if its pricing was ever published. */
    Optional<PhysicalCoursePricing> findByCourseId(String courseId);

    /** Stores the given pricing version as the course's current one and returns the stored state. */
    PhysicalCoursePricing save(PhysicalCoursePricing pricing);
}
