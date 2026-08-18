package com.menta.billing.infrastructure.persistence.mapper;

import com.menta.billing.domain.model.FulfillmentStatus;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Subscription;
import com.menta.billing.infrastructure.persistence.entity.SubscriptionJpaEntity;

public final class SubscriptionJpaMapper {

    private SubscriptionJpaMapper() {
    }

    public static Subscription toDomain(SubscriptionJpaEntity entity) {
        return new Subscription(
            entity.getId(), PaymentId.of(entity.getPaymentId()), entity.getVirtualCourseId(),
            FulfillmentStatus.valueOf(entity.getStatus())
        );
    }

    public static SubscriptionJpaEntity toEntity(Subscription subscription) {
        return new SubscriptionJpaEntity(
            subscription.getId(), subscription.getPaymentId().getValue(), subscription.getVirtualCourseId(),
            subscription.getStatus().name()
        );
    }
}
