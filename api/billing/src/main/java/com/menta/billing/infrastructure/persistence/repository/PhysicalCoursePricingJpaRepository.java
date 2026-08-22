package com.menta.billing.infrastructure.persistence.repository;

import com.menta.billing.infrastructure.persistence.entity.PhysicalCoursePricingJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhysicalCoursePricingJpaRepository extends JpaRepository<PhysicalCoursePricingJpaEntity, String> {
}
