package com.menta.billing.infrastructure.persistence.mapper;

import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.PhysicalCourseQuote;
import com.menta.billing.domain.model.PurchaseType;
import com.menta.billing.domain.model.QuoteAvailability;
import com.menta.billing.infrastructure.persistence.entity.PhysicalCourseQuoteJpaEntity;
import java.util.UUID;

/** Manual mapping between {@link PhysicalCourseQuoteJpaEntity} and the domain {@link PhysicalCourseQuote}. */
public final class PhysicalCourseQuoteJpaMapper {

    private PhysicalCourseQuoteJpaMapper() {
    }

    public static PhysicalCourseQuote toDomain(PhysicalCourseQuoteJpaEntity entity) {
        return PhysicalCourseQuote.reconstitute(
            UUID.fromString(entity.getId()),
            entity.getCourseId(),
            PurchaseType.valueOf(entity.getPurchaseType()),
            Money.of(entity.getMonthlyPriceAmount(), entity.getMonthlyPriceCurrency()),
            entity.getIndividualSurchargePercent(),
            entity.getPricingVersion(),
            entity.getScheduledSessionCount(),
            entity.getSelectedSessionId(),
            Money.of(entity.getAmountValue(), entity.getAmountCurrency()),
            QuoteAvailability.valueOf(entity.getAvailability()),
            entity.getCreatedAt(),
            entity.getExpiresAt()
        );
    }

    public static PhysicalCourseQuoteJpaEntity toEntity(PhysicalCourseQuote quote) {
        return new PhysicalCourseQuoteJpaEntity(
            quote.getId().toString(),
            quote.getCourseId(),
            quote.getPurchaseType().name(),
            quote.getMonthlyPrice().getAmount(),
            quote.getMonthlyPrice().getCurrency(),
            quote.getIndividualSurchargePercent(),
            quote.getPricingVersion(),
            quote.getScheduledSessionCount(),
            quote.getSelectedSessionId(),
            quote.getAmount().getAmount(),
            quote.getAmount().getCurrency(),
            quote.getAvailability().name(),
            quote.getCreatedAt(),
            quote.getExpiresAt()
        );
    }
}
