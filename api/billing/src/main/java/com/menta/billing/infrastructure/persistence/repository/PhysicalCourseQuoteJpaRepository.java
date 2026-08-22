package com.menta.billing.infrastructure.persistence.repository;

import com.menta.billing.infrastructure.persistence.entity.PhysicalCourseQuoteJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhysicalCourseQuoteJpaRepository extends JpaRepository<PhysicalCourseQuoteJpaEntity, String> {
}
