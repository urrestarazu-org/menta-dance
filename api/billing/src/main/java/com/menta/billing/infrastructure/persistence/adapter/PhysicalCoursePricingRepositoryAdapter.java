package com.menta.billing.infrastructure.persistence.adapter;

import com.menta.billing.application.port.out.PhysicalCoursePricingRepository;
import com.menta.billing.domain.model.PhysicalCoursePricing;
import com.menta.billing.infrastructure.persistence.mapper.PhysicalCoursePricingJpaMapper;
import com.menta.billing.infrastructure.persistence.repository.PhysicalCoursePricingJpaRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for {@link PhysicalCoursePricingRepository}. */
@Component
public class PhysicalCoursePricingRepositoryAdapter implements PhysicalCoursePricingRepository {

    private final PhysicalCoursePricingJpaRepository jpaRepository;

    public PhysicalCoursePricingRepositoryAdapter(PhysicalCoursePricingJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Optional<PhysicalCoursePricing> findByCourseId(String courseId) {
        return jpaRepository.findById(courseId).map(PhysicalCoursePricingJpaMapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public PhysicalCoursePricing save(PhysicalCoursePricing pricing) {
        return PhysicalCoursePricingJpaMapper.toDomain(
            jpaRepository.save(PhysicalCoursePricingJpaMapper.toEntity(pricing))
        );
    }
}
