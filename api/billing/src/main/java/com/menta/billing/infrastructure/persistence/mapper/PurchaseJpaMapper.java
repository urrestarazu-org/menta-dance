package com.menta.billing.infrastructure.persistence.mapper;

import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Purchase;
import com.menta.billing.infrastructure.persistence.entity.PurchaseJpaEntity;

public final class PurchaseJpaMapper {

    private PurchaseJpaMapper() {
    }

    public static Purchase toDomain(PurchaseJpaEntity entity) {
        return new Purchase(
            entity.getId(), PaymentId.of(entity.getPaymentId()), entity.getPhysicalSessionId(),
            com.menta.billing.domain.model.FulfillmentStatus.valueOf(entity.getStatus())
        );
    }

    public static PurchaseJpaEntity toEntity(Purchase purchase) {
        return new PurchaseJpaEntity(
            purchase.getId(), purchase.getPaymentId().getValue(), purchase.getPhysicalSessionId(),
            purchase.getStatus().name()
        );
    }
}
