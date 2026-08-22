package com.menta.billing.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.billing.domain.model.FulfillmentStatus;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.domain.model.Subscription;
import com.menta.billing.domain.model.SubscriptionStatus;
import com.menta.billing.infrastructure.persistence.entity.SubscriptionJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubscriptionJpaMapperTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();

    private static Subscription pending() {
        return Subscription.pendingCheckout(
            UUID.randomUUID(), PaymentId.generate(), USER_ID, PlanId.generate(), "idem-1", NOW
        );
    }

    @Test
    void round_trips_a_pending_checkout() {
        Subscription original = pending().withCheckout("pref-1", "https://mp.example/pref-1");

        Subscription restored = SubscriptionJpaMapper.toDomain(
            SubscriptionJpaMapper.toEntity(original), List.of()
        );

        assertThat(restored.getId()).isEqualTo(original.getId());
        assertThat(restored.getPaymentId()).isEqualTo(original.getPaymentId());
        assertThat(restored.getUserId()).isEqualTo(USER_ID);
        assertThat(restored.getPlanId()).isEqualTo(original.getPlanId());
        assertThat(restored.getIdempotencyKey()).isEqualTo("idem-1");
        assertThat(restored.getStatus()).isEqualTo(SubscriptionStatus.PENDING);
        assertThat(restored.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PENDING_FULFILLMENT);
        assertThat(restored.getProviderPreferenceId()).contains("pref-1");
        assertThat(restored.getCheckoutUrl()).contains("https://mp.example/pref-1");
        assertThat(restored.getStartDate()).isEmpty();
        assertThat(restored.getEndDate()).isEmpty();
        assertThat(restored.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void round_trips_an_activated_subscription_with_its_snapshot() {
        Subscription original = pending().activate(NOW, 30, List.of("course-1", "course-2")).assigned();

        Subscription restored = SubscriptionJpaMapper.toDomain(
            SubscriptionJpaMapper.toEntity(original), List.of("course-1", "course-2")
        );

        assertThat(restored.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(restored.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.ASSIGNED);
        assertThat(restored.getStartDate()).contains(NOW);
        assertThat(restored.getEndDate()).contains(Instant.parse("2026-09-17T12:00:00Z"));
        assertThat(restored.getCourseIds()).containsExactly("course-1", "course-2");
        assertThat(restored.grantsAccess()).isTrue();
    }

    /**
     * The whole partial-uniqueness scheme rests on this projection: while the
     * subscription occupies the user's slot the column carries the user id,
     * and once it does not it must be NULL so a UNIQUE index stops blocking.
     */
    @Test
    void active_user_id_mirrors_whether_the_subscription_occupies_the_slot() {
        SubscriptionJpaEntity pendingEntity = SubscriptionJpaMapper.toEntity(pending());
        SubscriptionJpaEntity activeEntity =
            SubscriptionJpaMapper.toEntity(pending().activate(NOW, 30, List.of()));
        SubscriptionJpaEntity cancelledEntity = SubscriptionJpaMapper.toEntity(pending().cancelled());

        assertThat(pendingEntity.getActiveUserId()).isEqualTo(USER_ID);
        assertThat(activeEntity.getActiveUserId()).isEqualTo(USER_ID);
        assertThat(cancelledEntity.getActiveUserId()).isNull();
    }
}
