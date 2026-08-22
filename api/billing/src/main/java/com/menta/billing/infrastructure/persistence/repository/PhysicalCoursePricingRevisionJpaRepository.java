package com.menta.billing.infrastructure.persistence.repository;

import com.menta.billing.infrastructure.persistence.entity.PhysicalCoursePricingRevisionJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhysicalCoursePricingRevisionJpaRepository
    extends JpaRepository<PhysicalCoursePricingRevisionJpaEntity, UUID> {
}
