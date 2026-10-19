package com.menta.billing.infrastructure.persistence.repository;

import com.menta.billing.infrastructure.persistence.entity.PhysicalCoursePricingJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository over {@code billing_physical_course_pricing}, keyed by course id.
 *
 * <p>Holds only the current pricing of each course and is used only through {@code
 * PhysicalCoursePricingRepositoryAdapter}.
 */
public interface PhysicalCoursePricingJpaRepository extends JpaRepository<PhysicalCoursePricingJpaEntity, String> {
}
