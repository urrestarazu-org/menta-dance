package com.menta.billing.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.PhysicalCoursePricing;
import com.menta.billing.infrastructure.persistence.entity.PhysicalCoursePricingJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.PhysicalCoursePricingJpaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PhysicalCoursePricingRepositoryAdapterTest {

    private PhysicalCoursePricingJpaRepository jpaRepository;
    private PhysicalCoursePricingRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        jpaRepository = mock(PhysicalCoursePricingJpaRepository.class);
        adapter = new PhysicalCoursePricingRepositoryAdapter(jpaRepository);
    }

    @Test
    void findByCourseId_maps_when_present() {
        Instant updatedAt = Instant.now();
        PhysicalCoursePricingJpaEntity entity = new PhysicalCoursePricingJpaEntity(
            "course-1", new BigDecimal("150.00"), "ARS", new BigDecimal("10.00"), 2, updatedAt
        );
        when(jpaRepository.findById("course-1")).thenReturn(Optional.of(entity));

        Optional<PhysicalCoursePricing> pricing = adapter.findByCourseId("course-1");

        assertThat(pricing).isPresent();
        assertThat(pricing.get().getVersion()).isEqualTo(2);
        assertThat(pricing.get().getMonthlyPrice()).isEqualTo(Money.of(new BigDecimal("150.00"), "ARS"));
    }

    @Test
    void findByCourseId_empty_when_absent() {
        when(jpaRepository.findById("course-1")).thenReturn(Optional.empty());

        assertThat(adapter.findByCourseId("course-1")).isEmpty();
    }

    @Test
    void save_persists_and_returns_the_mapped_domain_object() {
        PhysicalCoursePricing pricing = PhysicalCoursePricing.createFirstVersion(
            "course-1", Money.of(new BigDecimal("150.00"), "ARS"), new BigDecimal("10"), Instant.now()
        );
        when(jpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PhysicalCoursePricing result = adapter.save(pricing);

        assertThat(result.getCourseId()).isEqualTo("course-1");
        assertThat(result.getVersion()).isEqualTo(1);
    }
}
