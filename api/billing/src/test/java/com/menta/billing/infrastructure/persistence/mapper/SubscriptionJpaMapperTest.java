package com.menta.billing.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.billing.domain.model.FulfillmentStatus;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.domain.model.Subscription;
import com.menta.billing.domain.model.SubscriptionStatus;
import com.menta.billing.domain.model.SubscriptionType;
import com.menta.billing.domain.model.TrialGrant;
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

    @Test
    void round_trips_the_cancellation_audit_trail() {
        UUID adminId = UUID.randomUUID();
        Subscription original = pending().activate(NOW, 30, List.of("course-1"))
            .cancel(adminId, "fraude confirmado", NOW.plusSeconds(3600));

        Subscription restored = SubscriptionJpaMapper.toDomain(
            SubscriptionJpaMapper.toEntity(original), List.of("course-1")
        );

        assertThat(restored.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(restored.getCancellation()).isPresent();
        assertThat(restored.getCancellation().get().at()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(restored.getCancellation().get().by()).isEqualTo(adminId);
        assertThat(restored.getCancellation().get().reason()).isEqualTo("fraude confirmado");
    }

    @Test
    void round_trips_a_legacy_cancelled_row_with_no_cancellation_actor_or_reason() {
        SubscriptionJpaEntity legacyCancelledEntity = SubscriptionJpaMapper.toEntity(pending().cancelled());

        Subscription restored = SubscriptionJpaMapper.toDomain(legacyCancelledEntity, List.of());

        assertThat(restored.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(restored.getCancellation()).isEmpty();
    }

    // --- type/grant/version (US-BILLING-012) ---

    @Test
    void round_trips_a_paid_subscription_with_no_grant_columns_populated() {
        Subscription original = pending().activate(NOW, 30, List.of("course-1"));

        SubscriptionJpaEntity entity = SubscriptionJpaMapper.toEntity(original);
        Subscription restored = SubscriptionJpaMapper.toDomain(entity, List.of("course-1"));

        assertThat(entity.getType()).isEqualTo("PAID");
        assertThat(entity.getPaymentId()).isNotNull();
        assertThat(entity.getGrantedAt()).isNull();
        assertThat(entity.getGrantedBy()).isNull();
        assertThat(entity.getGrantReason()).isNull();
        assertThat(entity.getGrantDays()).isNull();
        assertThat(restored.getType()).isEqualTo(SubscriptionType.PAID);
        assertThat(restored.getTrialGrant()).isEmpty();
        assertThat(restored.getPaymentId()).isPresent();
    }

    @Test
    void round_trips_a_trial_subscription_with_no_payment_and_its_full_grant_audit() {
        UUID adminId = UUID.randomUUID();
        TrialGrant grant = new TrialGrant(NOW, adminId, "evaluación de producto", 14);
        Subscription original = Subscription.trial(
            UUID.randomUUID(), USER_ID, PlanId.generate(), NOW, List.of("course-1", "course-2"), grant
        );

        SubscriptionJpaEntity entity = SubscriptionJpaMapper.toEntity(original);
        Subscription restored = SubscriptionJpaMapper.toDomain(entity, List.of("course-1", "course-2"));

        assertThat(entity.getType()).isEqualTo("TRIAL");
        assertThat(entity.getPaymentId()).isNull();
        assertThat(entity.getGrantedAt()).isEqualTo(NOW);
        assertThat(entity.getGrantedBy()).isEqualTo(adminId);
        assertThat(entity.getGrantReason()).isEqualTo("evaluación de producto");
        assertThat(entity.getGrantDays()).isEqualTo(14);
        assertThat(restored.getType()).isEqualTo(SubscriptionType.TRIAL);
        assertThat(restored.getPaymentId()).isEmpty();
        assertThat(restored.getTrialGrant()).contains(grant);
    }

    @Test
    void preserves_the_optimistic_lock_version_in_both_directions() {
        Subscription rehydrated = new Subscription(
            UUID.randomUUID(), PaymentId.generate(), USER_ID, PlanId.generate(), "idem-1",
            SubscriptionStatus.ACTIVE, FulfillmentStatus.ASSIGNED, NOW, NOW.plusSeconds(100), List.of("course-1"),
            null, null, NOW, null, SubscriptionType.PAID, null, 5L
        );

        SubscriptionJpaEntity entity = SubscriptionJpaMapper.toEntity(rehydrated);
        Subscription restored = SubscriptionJpaMapper.toDomain(entity, List.of("course-1"));

        assertThat(entity.getVersion()).isEqualTo(5L);
        assertThat(restored.getVersion()).isEqualTo(5L);
    }
}
