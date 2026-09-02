package com.menta.billing.infrastructure.persistence.mapper;

import com.menta.billing.domain.model.Cancellation;
import com.menta.billing.domain.model.FulfillmentStatus;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.domain.model.Subscription;
import com.menta.billing.domain.model.SubscriptionStatus;
import com.menta.billing.domain.model.SubscriptionType;
import com.menta.billing.domain.model.TrialGrant;
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
            entity.getPaymentId() == null ? null : PaymentId.of(entity.getPaymentId()),
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
            entity.getCreatedAt(),
            toCancellation(entity),
            SubscriptionType.valueOf(entity.getType()),
            toTrialGrant(entity),
            entity.getVersion()
        );
    }

    public static SubscriptionJpaEntity toEntity(Subscription subscription) {
        Cancellation cancellation = subscription.getCancellation().orElse(null);
        TrialGrant grant = subscription.getTrialGrant().orElse(null);
        return new SubscriptionJpaEntity(
            subscription.getId(),
            subscription.getPaymentId().map(PaymentId::getValue).orElse(null),
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
            subscription.getCreatedAt(),
            cancellation != null ? cancellation.at() : null,
            cancellation != null ? cancellation.by() : null,
            cancellation != null ? cancellation.reason() : null,
            subscription.getType().name(),
            grant != null ? grant.at() : null,
            grant != null ? grant.by() : null,
            grant != null ? grant.reason() : null,
            grant != null ? grant.days() : null,
            subscription.getVersion()
        );
    }

    /** NULL {@code cancelled_at} means this row was never cancelled through {@code Subscription.cancel} — pre-existing terminal-payment rows map to {@link java.util.Optional#empty()} (A8 / V17 migration note). */
    private static Cancellation toCancellation(SubscriptionJpaEntity entity) {
        if (entity.getCancelledAt() == null) {
            return null;
        }
        return new Cancellation(entity.getCancelledAt(), entity.getCancelledBy(), entity.getCancellationReason());
    }

    /** NULL {@code granted_at} means this row is not a TRIAL — every PAID row back-filled by V18 (US-BILLING-012 A17). */
    private static TrialGrant toTrialGrant(SubscriptionJpaEntity entity) {
        if (entity.getGrantedAt() == null) {
            return null;
        }
        return new TrialGrant(
            entity.getGrantedAt(), entity.getGrantedBy(), entity.getGrantReason(), entity.getGrantDays()
        );
    }
}
