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

    Optional<PhysicalCoursePricing> findByCourseId(String courseId);

    PhysicalCoursePricing save(PhysicalCoursePricing pricing);
}
