package com.menta.billing.infrastructure.persistence.mapper;

import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.PhysicalCoursePricing;
import com.menta.billing.infrastructure.persistence.entity.PhysicalCoursePricingJpaEntity;

/** Manual mapping between {@link PhysicalCoursePricingJpaEntity} and the domain {@link PhysicalCoursePricing}. */
public final class PhysicalCoursePricingJpaMapper {

    private PhysicalCoursePricingJpaMapper() {
    }

    /**
     * Rebuilds the pricing through {@code PhysicalCoursePricing.reconstitute}, keeping the stored
     * version and update time instead of computing new ones.
     */
    public static PhysicalCoursePricing toDomain(PhysicalCoursePricingJpaEntity entity) {
        return PhysicalCoursePricing.reconstitute(
            entity.getCourseId(),
            Money.of(entity.getMonthlyPrice(), entity.getMonthlyPriceCurrency()),
            entity.getIndividualSurchargePercent(),
            entity.getVersion(),
            entity.getUpdatedAt()
        );
    }

    /** Flattens the pricing into a row, splitting the monthly price into an amount and a currency. */
    public static PhysicalCoursePricingJpaEntity toEntity(PhysicalCoursePricing pricing) {
        return new PhysicalCoursePricingJpaEntity(
            pricing.getCourseId(),
            pricing.getMonthlyPrice().getAmount(),
            pricing.getMonthlyPrice().getCurrency(),
            pricing.getIndividualSurchargePercent(),
            pricing.getVersion(),
            pricing.getUpdatedAt()
        );
    }
}
