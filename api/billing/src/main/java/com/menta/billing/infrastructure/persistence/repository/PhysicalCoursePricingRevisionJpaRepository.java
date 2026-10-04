package com.menta.billing.infrastructure.persistence.repository;

import com.menta.billing.infrastructure.persistence.entity.PhysicalCoursePricingRevisionJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository over the {@code billing_physical_course_pricing_revisions} audit table.
 *
 * <p>Used only through {@code PhysicalCoursePricingRevisionRepositoryAdapter}.
 */
public interface PhysicalCoursePricingRevisionJpaRepository
    extends JpaRepository<PhysicalCoursePricingRevisionJpaEntity, UUID> {
}
