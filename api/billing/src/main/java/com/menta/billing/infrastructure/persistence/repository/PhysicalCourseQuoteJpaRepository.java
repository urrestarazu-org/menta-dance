package com.menta.billing.infrastructure.persistence.repository;

import com.menta.billing.infrastructure.persistence.entity.PhysicalCourseQuoteJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository over {@code billing_physical_course_quotes}, keyed by the quote id as
 * text.
 *
 * <p>Used only through {@code PhysicalCourseQuoteRepositoryAdapter}.
 */
public interface PhysicalCourseQuoteJpaRepository extends JpaRepository<PhysicalCourseQuoteJpaEntity, String> {
}
