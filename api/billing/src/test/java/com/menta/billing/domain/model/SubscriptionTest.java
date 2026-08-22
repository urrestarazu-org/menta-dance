package com.menta.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubscriptionTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-18T10:00:00Z");
    private static final Instant CONFIRMED_AT = Instant.parse("2026-08-18T12:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();

    private static Subscription pending() {
        return Subscription.pendingCheckout(
            UUID.randomUUID(), PaymentId.generate(), USER_ID, PlanId.generate(), "idem-1", CREATED_AT
        );
    }

    @Test
    void pendingCheckout_starts_pending_with_no_vigencia_and_an_empty_snapshot() {
        Subscription subscription = pending();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PENDING);
        assertThat(subscription.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PENDING_FULFILLMENT);
        assertThat(subscription.getStartDate()).isEmpty();
        assertThat(subscription.getEndDate()).isEmpty();
        assertThat(subscription.getCourseIds()).isEmpty();
        assertThat(subscription.getProviderPreferenceId()).isEmpty();
        assertThat(subscription.getCheckoutUrl()).isEmpty();
        assertThat(subscription.getIdempotencyKey()).isEqualTo("idem-1");
        assertThat(subscription.getUserId()).isEqualTo(USER_ID);
        assertThat(subscription.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(subscription.grantsAccess()).isFalse();
        assertThat(subscription.isActivated()).isFalse();
        assertThat(subscription.occupiesUserSlot()).isTrue();
    }

    @Test
    void withCheckout_records_the_preference_and_its_url() {
        Subscription subscription = pending().withCheckout("pref-1", "https://mp.example/checkout/pref-1");

        assertThat(subscription.getProviderPreferenceId()).contains("pref-1");
        assertThat(subscription.getCheckoutUrl()).contains("https://mp.example/checkout/pref-1");
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PENDING);
    }

    @Test
    void activate_sets_vigencia_from_the_plan_duration_and_freezes_the_course_snapshot() {
        Subscription activated = pending().activate(CONFIRMED_AT, 30, List.of("course-1", "course-2"));

        assertThat(activated.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(activated.getStartDate()).contains(CONFIRMED_AT);
        assertThat(activated.getEndDate()).contains(Instant.parse("2026-09-17T12:00:00Z"));
        assertThat(activated.getCourseIds()).containsExactly("course-1", "course-2");
        assertThat(activated.isActivated()).isTrue();
        assertThat(activated.occupiesUserSlot()).isTrue();
    }

    @Test
    void activate_is_idempotent_once_active() {
        Subscription activated = pending().activate(CONFIRMED_AT, 30, List.of("course-1"));

        Subscription replayed = activated.activate(CONFIRMED_AT.plusSeconds(86400), 90, List.of("course-9"));

        assertThat(replayed).isSameAs(activated);
        assertThat(replayed.getStartDate()).contains(CONFIRMED_AT);
        assertThat(replayed.getCourseIds()).containsExactly("course-1");
    }

    @Test
    void activate_never_resurrects_a_cancelled_subscription() {
        Subscription cancelled = pending().cancelled();

        assertThat(cancelled.activate(CONFIRMED_AT, 30, List.of("course-1"))).isSameAs(cancelled);
    }

    @Test
    void activate_rejects_a_non_positive_duration() {
        Subscription subscription = pending();

        assertThatThrownBy(() -> subscription.activate(CONFIRMED_AT, 0, List.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void activate_rejects_a_null_confirmation_instant() {
        Subscription subscription = pending();

        assertThatThrownBy(() -> subscription.activate(null, 30, List.of()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void cancelled_releases_the_user_slot() {
        Subscription cancelled = pending().cancelled();

        assertThat(cancelled.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(cancelled.occupiesUserSlot()).isFalse();
        assertThat(cancelled.grantsAccess()).isFalse();
    }

    @Test
    void cancelled_is_a_no_op_once_the_slot_is_already_released() {
        Subscription cancelled = pending().cancelled();

        assertThat(cancelled.cancelled()).isSameAs(cancelled);
    }

    @Test
    void assigned_grants_access_only_together_with_an_active_status() {
        Subscription pendingAssigned = pending().assigned();
        Subscription activeAssigned = pending().activate(CONFIRMED_AT, 30, List.of("course-1")).assigned();

        assertThat(pendingAssigned.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.ASSIGNED);
        assertThat(pendingAssigned.grantsAccess()).isFalse();
        assertThat(activeAssigned.grantsAccess()).isTrue();
    }

    @Test
    void exception_never_grants_access_but_leaves_the_subscription_active() {
        Subscription subscription = pending().activate(CONFIRMED_AT, 30, List.of("course-1")).exception();

        assertThat(subscription.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.EXCEPTION);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.grantsAccess()).isFalse();
    }

    @Test
    void transitions_preserve_identity_plan_and_payment() {
        Subscription original = pending();

        for (Subscription derived : List.of(
            original.assigned(), original.exception(), original.cancelled(),
            original.withCheckout("pref", "url"), original.activate(CONFIRMED_AT, 30, List.of())
        )) {
            assertThat(derived.getId()).isEqualTo(original.getId());
            assertThat(derived.getPaymentId()).isEqualTo(original.getPaymentId());
            assertThat(derived.getPlanId()).isEqualTo(original.getPlanId());
            assertThat(derived.getUserId()).isEqualTo(original.getUserId());
            assertThat(derived.getIdempotencyKey()).isEqualTo(original.getIdempotencyKey());
            assertThat(derived.getCreatedAt()).isEqualTo(original.getCreatedAt());
        }
    }

    @Test
    void the_course_snapshot_is_immutable_and_defensively_copied() {
        List<String> mutable = new java.util.ArrayList<>(List.of("course-1"));
        Subscription subscription = pending().activate(CONFIRMED_AT, 30, mutable);
        mutable.add("course-2");

        assertThat(subscription.getCourseIds()).containsExactly("course-1");
        assertThatThrownBy(() -> subscription.getCourseIds().add("course-3"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejects_null_or_blank_required_fields() {
        assertThatThrownBy(() -> Subscription.pendingCheckout(
            null, PaymentId.generate(), USER_ID, PlanId.generate(), "idem-1", CREATED_AT
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Subscription.pendingCheckout(
            UUID.randomUUID(), PaymentId.generate(), USER_ID, PlanId.generate(), " ", CREATED_AT
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Subscription(
            UUID.randomUUID(), PaymentId.generate(), USER_ID, PlanId.generate(), "idem-1",
            SubscriptionStatus.PENDING, FulfillmentStatus.PENDING_FULFILLMENT, null, null, null, null, null,
            CREATED_AT
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void occupiesUserSlot_is_true_only_for_pending_and_active() {
        assertThat(SubscriptionStatus.PENDING.occupiesUserSlot()).isTrue();
        assertThat(SubscriptionStatus.ACTIVE.occupiesUserSlot()).isTrue();
        assertThat(SubscriptionStatus.EXPIRED.occupiesUserSlot()).isFalse();
        assertThat(SubscriptionStatus.CANCELLED.occupiesUserSlot()).isFalse();
    }
}
