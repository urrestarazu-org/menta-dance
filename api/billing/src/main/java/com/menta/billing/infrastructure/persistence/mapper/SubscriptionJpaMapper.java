package com.menta.billing.infrastructure.persistence.mapper;

import com.menta.billing.domain.model.FulfillmentStatus;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.domain.model.Subscription;
import com.menta.billing.domain.model.SubscriptionStatus;
import com.menta.billing.infrastructure.persistence.entity.SubscriptionJpaEntity;
import java.util.List;

/**
 * Maps {@link Subscription} to and from {@code billing_subscriptions}.
 *
 * <p>Sole writer of {@code active_user_id}: it is derived from the status,
 * never set by a caller, so the partial-uniqueness invariant cannot drift
 * from the domain state it projects (see {@link SubscriptionJpaEntity}).</p>
 */
public final class SubscriptionJpaMapper {

    private SubscriptionJpaMapper() {
    }

    public static Subscription toDomain(SubscriptionJpaEntity entity, List<String> courseIds) {
        return new Subscription(
            entity.getId(),
            PaymentId.of(entity.getPaymentId()),
            entity.getUserId(),
            PlanId.of(entity.getPlanId()),
            entity.getIdempotencyKey(),
            SubscriptionStatus.valueOf(entity.getStatus()),
            FulfillmentStatus.valueOf(entity.getFulfillmentStatus()),
            entity.getStartDate(),
            entity.getEndDate(),
            courseIds,
            entity.getProviderPreferenceId(),
            entity.getCheckoutUrl(),
            entity.getCreatedAt()
        );
    }

    public static SubscriptionJpaEntity toEntity(Subscription subscription) {
        return new SubscriptionJpaEntity(
            subscription.getId(),
            subscription.getPaymentId().getValue(),
            subscription.getUserId(),
            subscription.getPlanId().getValue(),
            subscription.getIdempotencyKey(),
            subscription.occupiesUserSlot() ? subscription.getUserId() : null,
            subscription.getStatus().name(),
            subscription.getFulfillmentStatus().name(),
            subscription.getStartDate().orElse(null),
            subscription.getEndDate().orElse(null),
            subscription.getProviderPreferenceId().orElse(null),
            subscription.getCheckoutUrl().orElse(null),
            subscription.getCreatedAt()
        );
    }
}
