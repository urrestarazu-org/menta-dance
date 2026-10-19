package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.PhysicalCoursePricingResult;
import com.menta.billing.application.port.in.GetPhysicalCoursePricingUseCase;
import com.menta.billing.application.port.out.PhysicalCoursePricingRepository;
import com.menta.billing.domain.exception.PhysicalCoursePricingNotFoundException;

/** Implementation of {@link GetPhysicalCoursePricingUseCase}. */
public class GetPhysicalCoursePricingUseCaseImpl implements GetPhysicalCoursePricingUseCase {

    private final PhysicalCoursePricingRepository pricingRepository;

    public GetPhysicalCoursePricingUseCaseImpl(PhysicalCoursePricingRepository pricingRepository) {
        this.pricingRepository = pricingRepository;
    }

    @Override
    public PhysicalCoursePricingResult getPricing(String courseId) {
        return pricingRepository.findByCourseId(courseId)
            .map(PhysicalCoursePricingResultMapper::toResult)
            .orElseThrow(PhysicalCoursePricingNotFoundException::new);
    }
}
