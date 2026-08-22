package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.PhysicalCourseQuoteResult;
import com.menta.billing.domain.model.PhysicalCourseQuote;

final class PhysicalCourseQuoteResultMapper {

    private PhysicalCourseQuoteResultMapper() {
    }

    static PhysicalCourseQuoteResult toResult(PhysicalCourseQuote quote) {
        return new PhysicalCourseQuoteResult(
            quote.getId(),
            quote.getCourseId(),
            quote.getPurchaseType(),
            quote.getScheduledSessionCount(),
            quote.getSelectedSessionId(),
            quote.getAmount().getAmount(),
            quote.getAmount().getCurrency(),
            quote.getAvailability(),
            quote.getPricingVersion(),
            quote.getCreatedAt(),
            quote.getExpiresAt()
        );
    }
}
