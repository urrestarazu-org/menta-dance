package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.PhysicalCoursePricingResult;
import com.menta.billing.application.port.out.PhysicalCoursePricingRepository;
import com.menta.billing.domain.exception.PhysicalCoursePricingNotFoundException;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.PhysicalCoursePricing;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetPhysicalCoursePricingUseCaseImplTest {

    private static final String COURSE_ID = "course-1";

    private PhysicalCoursePricingRepository pricingRepository;
    private GetPhysicalCoursePricingUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        pricingRepository = mock(PhysicalCoursePricingRepository.class);
        useCase = new GetPhysicalCoursePricingUseCaseImpl(pricingRepository);
    }

    @Test
    void returns_the_current_pricing_when_published() {
        PhysicalCoursePricing pricing = PhysicalCoursePricing.createFirstVersion(
            COURSE_ID, Money.of(new BigDecimal("100.00"), "ARS"), new BigDecimal("10"), Instant.now()
        );
        when(pricingRepository.findByCourseId(COURSE_ID)).thenReturn(Optional.of(pricing));

        PhysicalCoursePricingResult result = useCase.getPricing(COURSE_ID);

        assertThat(result.courseId()).isEqualTo(COURSE_ID);
        assertThat(result.version()).isEqualTo(1);
    }

    @Test
    void throws_when_no_pricing_was_ever_published() {
        when(pricingRepository.findByCourseId(COURSE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.getPricing(COURSE_ID))
            .isInstanceOf(PhysicalCoursePricingNotFoundException.class);
    }
}
