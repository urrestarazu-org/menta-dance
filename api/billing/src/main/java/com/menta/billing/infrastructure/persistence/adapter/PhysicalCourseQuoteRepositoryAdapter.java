package com.menta.billing.infrastructure.persistence.adapter;

import com.menta.billing.application.port.out.PhysicalCourseQuoteRepository;
import com.menta.billing.domain.model.PhysicalCourseQuote;
import com.menta.billing.infrastructure.persistence.mapper.PhysicalCourseQuoteJpaMapper;
import com.menta.billing.infrastructure.persistence.repository.PhysicalCourseQuoteJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for {@link PhysicalCourseQuoteRepository}. */
@Component
public class PhysicalCourseQuoteRepositoryAdapter implements PhysicalCourseQuoteRepository {

    private final PhysicalCourseQuoteJpaRepository jpaRepository;

    public PhysicalCourseQuoteRepositoryAdapter(PhysicalCourseQuoteJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public PhysicalCourseQuote save(PhysicalCourseQuote quote) {
        return PhysicalCourseQuoteJpaMapper.toDomain(
            jpaRepository.save(PhysicalCourseQuoteJpaMapper.toEntity(quote))
        );
    }
}
