package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.PhysicalCoursePricingResult;
import com.menta.billing.domain.model.PhysicalCoursePricing;

final class PhysicalCoursePricingResultMapper {

    private PhysicalCoursePricingResultMapper() {
    }

    static PhysicalCoursePricingResult toResult(PhysicalCoursePricing pricing) {
        return new PhysicalCoursePricingResult(
            pricing.getCourseId(),
            pricing.getMonthlyPrice().getAmount(),
            pricing.getMonthlyPrice().getCurrency(),
            pricing.getIndividualSurchargePercent(),
            pricing.getVersion(),
            pricing.getUpdatedAt()
        );
    }

    /** Human-readable snapshot for the audit trail — not a wire format. */
    static String toAuditSnapshot(PhysicalCoursePricing pricing) {
        return "monthlyPrice=" + pricing.getMonthlyPrice().getAmount()
            + ", currency=" + pricing.getMonthlyPrice().getCurrency()
            + ", individualSurchargePercent=" + pricing.getIndividualSurchargePercent()
            + ", version=" + pricing.getVersion();
    }
}
