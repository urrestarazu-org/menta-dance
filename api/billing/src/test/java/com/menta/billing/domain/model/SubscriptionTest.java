package com.menta.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubscriptionTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-18T10:00:00Z");
    private static final Instant CONFIRMED_AT = Instant.parse("2026-08-18T12:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();

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
            CREATED_AT, null, SubscriptionType.PAID, null
        )).isInstanceOf(NullPointerException.class);
    }

    // --- trial(...) (US-BILLING-012) ---

    @Test
    void trial_yields_an_active_assigned_trial_with_no_payment_and_the_admins_own_duration() {
        TrialGrant grant = new TrialGrant(CONFIRMED_AT, ADMIN_ID, "evaluación de producto", 14);

        Subscription trial = Subscription.trial(
            UUID.randomUUID(), USER_ID, PlanId.generate(), CONFIRMED_AT,
            List.of("course-1", "course-2"), grant
        );

        assertThat(trial.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(trial.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.ASSIGNED);
        assertThat(trial.getType()).isEqualTo(SubscriptionType.TRIAL);
        assertThat(trial.getPaymentId()).isEmpty();
        assertThat(trial.grantsAccess()).isTrue();
        assertThat(trial.getStartDate()).contains(CONFIRMED_AT);
        assertThat(trial.getEndDate()).contains(CONFIRMED_AT.plus(14, ChronoUnit.DAYS));
        assertThat(trial.getCourseIds()).containsExactly("course-1", "course-2");
        assertThat(trial.getTrialGrant()).contains(grant);
    }

    /** Triangulation: a days value with no relation to any plan's durationDays proves no derivation from the plan. */
    @Test
    void trial_endDate_is_derived_only_from_the_admins_days_never_a_plan_duration() {
        TrialGrant grant = new TrialGrant(CONFIRMED_AT, ADMIN_ID, "cortesía", 5);

        Subscription trial = Subscription.trial(
            UUID.randomUUID(), USER_ID, PlanId.generate(), CONFIRMED_AT, List.of("course-9"), grant
        );

        assertThat(trial.getEndDate()).contains(CONFIRMED_AT.plus(5, ChronoUnit.DAYS));
    }

    /**
     * Regression (PR #163 review): {@code trial(...)} used to take a separate {@code int days}
     * parameter alongside {@code grant}, so a caller could pass a {@code days} value that
     * disagreed with {@code grant.days()} — granting real access for one duration while the
     * audit trail recorded another. There is now no separate parameter to diverge: {@code
     * endDate} can only ever come from {@code grant.days()}.
     */
    @Test
    void trial_derives_endDate_exclusively_from_the_grant_days_no_separate_days_parameter_exists() {
        TrialGrant grant = new TrialGrant(CONFIRMED_AT, ADMIN_ID, "evaluación de producto", 30);

        Subscription trial = Subscription.trial(
            UUID.randomUUID(), USER_ID, PlanId.generate(), CONFIRMED_AT, List.of("course-1"), grant
        );

        assertThat(trial.getEndDate()).contains(CONFIRMED_AT.plus(30, ChronoUnit.DAYS));
        assertThat(trial.getTrialGrant()).contains(grant);
    }

    // --- expire(at) (US-BILLING-012, A13) ---

    @Test
    void expire_is_a_no_op_from_every_non_active_status() {
        Subscription pendingSubscription = pending();
        Subscription cancelledSubscription = pending().cancelled();

        assertThat(pendingSubscription.expire(CONFIRMED_AT)).isSameAs(pendingSubscription);
        assertThat(cancelledSubscription.expire(CONFIRMED_AT)).isSameAs(cancelledSubscription);
    }

    @Test
    void expire_throws_when_endDate_has_not_passed_and_expires_exactly_at_the_boundary() {
        Subscription active = pending().activate(CONFIRMED_AT, 30, List.of("course-1"));
        Instant endDate = active.getEndDate().orElseThrow();

        assertThatThrownBy(() -> active.expire(endDate.minusSeconds(1)))
            .isInstanceOf(IllegalStateException.class);

        Subscription expired = active.expire(endDate);
        assertThat(expired.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(expired.getEndDate()).isEqualTo(active.getEndDate());
    }

    /** Triangulation: a TRIAL expires through the exact same guard as a PAID subscription. */
    @Test
    void expire_moves_a_stale_trial_to_expired_too() {
        TrialGrant grant = new TrialGrant(CONFIRMED_AT, ADMIN_ID, "cortesía", 5);
        Subscription trial = Subscription.trial(
            UUID.randomUUID(), USER_ID, PlanId.generate(), CONFIRMED_AT, List.of("course-1"), grant
        );

        Subscription expired = trial.expire(trial.getEndDate().orElseThrow());

        assertThat(expired.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(expired.getType()).isEqualTo(SubscriptionType.TRIAL);
        assertThat(expired.getEndDate()).isEqualTo(trial.getEndDate());
    }

    // --- type/payment/grant invariants (US-BILLING-012, A17) ---

    @Test
    void rejects_a_paid_subscription_with_no_payment() {
        assertThatThrownBy(() -> new Subscription(
            UUID.randomUUID(), null, USER_ID, PlanId.generate(), "idem-1", SubscriptionStatus.PENDING,
            FulfillmentStatus.PENDING_FULFILLMENT, null, null, List.of(), null, null, CREATED_AT, null,
            SubscriptionType.PAID, null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_paid_subscription_carrying_a_trial_grant() {
        assertThatThrownBy(() -> new Subscription(
            UUID.randomUUID(), PaymentId.generate(), USER_ID, PlanId.generate(), "idem-1",
            SubscriptionStatus.PENDING, FulfillmentStatus.PENDING_FULFILLMENT, null, null, List.of(), null, null,
            CREATED_AT, null, SubscriptionType.PAID, new TrialGrant(CREATED_AT, ADMIN_ID, "reason", 5)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_trial_subscription_carrying_a_payment() {
        assertThatThrownBy(() -> new Subscription(
            UUID.randomUUID(), PaymentId.generate(), USER_ID, PlanId.generate(), "idem-1",
            SubscriptionStatus.ACTIVE, FulfillmentStatus.ASSIGNED, CREATED_AT, CREATED_AT.plusSeconds(10),
            List.of(), null, null, CREATED_AT, null, SubscriptionType.TRIAL,
            new TrialGrant(CREATED_AT, ADMIN_ID, "reason", 5)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_trial_subscription_with_no_grant() {
        assertThatThrownBy(() -> new Subscription(
            UUID.randomUUID(), null, USER_ID, PlanId.generate(), "idem-1", SubscriptionStatus.ACTIVE,
            FulfillmentStatus.ASSIGNED, CREATED_AT, CREATED_AT.plusSeconds(10), List.of(), null, null, CREATED_AT,
            null, SubscriptionType.TRIAL, null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void the_legal_type_pairings_survive_a_full_transition_chain() {
        Subscription trial = Subscription.trial(
            UUID.randomUUID(), USER_ID, PlanId.generate(), CONFIRMED_AT, List.of("course-1"),
            new TrialGrant(CONFIRMED_AT, ADMIN_ID, "cortesía", 5)
        );
        Subscription expiredTrial = trial.expire(trial.getEndDate().orElseThrow());
        assertThat(expiredTrial.getType()).isEqualTo(SubscriptionType.TRIAL);
        assertThat(expiredTrial.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);

        Subscription cancelledPaid = pending().activate(CONFIRMED_AT, 30, List.of("course-1"))
            .cancel(USER_ID, null, CONFIRMED_AT.plusSeconds(10));
        assertThat(cancelledPaid.getType()).isEqualTo(SubscriptionType.PAID);
        assertThat(cancelledPaid.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    // --- version (US-BILLING-012, A14) ---

    @Test
    void version_survives_copy_transitions() {
        Subscription rehydrated = new Subscription(
            UUID.randomUUID(), PaymentId.generate(), USER_ID, PlanId.generate(), "idem-1",
            SubscriptionStatus.ACTIVE, FulfillmentStatus.ASSIGNED, CONFIRMED_AT, CONFIRMED_AT.plusSeconds(100),
            List.of("course-1"), null, null, CREATED_AT, null, SubscriptionType.PAID, null, 7L
        );

        Subscription cancelled = rehydrated.cancel(USER_ID, null, CONFIRMED_AT.plusSeconds(10));

        assertThat(cancelled.getVersion()).isEqualTo(7L);
    }

    @Test
    void a_freshly_created_subscription_starts_at_version_zero() {
        assertThat(pending().getVersion()).isEqualTo(0L);
    }

    // --- getPaymentId() ripple (US-BILLING-012, A1) ---

    @Test
    void getPaymentId_is_present_for_a_paid_subscription_and_empty_for_a_trial() {
        Subscription paid = pending();
        Subscription trial = Subscription.trial(
            UUID.randomUUID(), USER_ID, PlanId.generate(), CONFIRMED_AT, List.of(),
            new TrialGrant(CONFIRMED_AT, ADMIN_ID, "cortesía", 5)
        );

        assertThat(paid.getPaymentId()).isPresent();
        assertThat(trial.getPaymentId()).isEmpty();
    }

    @Test
    void occupiesUserSlot_is_true_only_for_pending_and_active() {
        assertThat(SubscriptionStatus.PENDING.occupiesUserSlot()).isTrue();
        assertThat(SubscriptionStatus.ACTIVE.occupiesUserSlot()).isTrue();
        assertThat(SubscriptionStatus.EXPIRED.occupiesUserSlot()).isFalse();
        assertThat(SubscriptionStatus.CANCELLED.occupiesUserSlot()).isFalse();
    }

    @Test
    void cancel_from_active_sets_the_cancellation_audit_and_leaves_endDate_untouched() {
        Subscription active = pending().activate(CONFIRMED_AT, 30, List.of("course-1"));
        Instant cancelledAt = CONFIRMED_AT.plusSeconds(3600);

        Subscription cancelled = active.cancel(USER_ID, null, cancelledAt);

        assertThat(cancelled.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(cancelled.getEndDate()).isEqualTo(active.getEndDate());
        assertThat(cancelled.getCancellation()).isPresent();
        assertThat(cancelled.getCancellation().get().at()).isEqualTo(cancelledAt);
        assertThat(cancelled.getCancellation().get().by()).isEqualTo(USER_ID);
        assertThat(cancelled.getCancellation().get().reason()).isNull();
    }

    @Test
    void cancel_rejects_a_subscription_that_is_not_active() {
        Subscription pendingSubscription = pending();
        assertThatThrownBy(() -> pendingSubscription.cancel(USER_ID, null, CONFIRMED_AT))
            .isInstanceOf(IllegalStateException.class);

        Subscription alreadyCancelled = pending().activate(CONFIRMED_AT, 30, List.of())
            .cancel(USER_ID, null, CONFIRMED_AT.plusSeconds(10));
        assertThatThrownBy(() -> alreadyCancelled.cancel(USER_ID, null, CONFIRMED_AT.plusSeconds(20)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancel_stamps_cancelledBy_even_for_self_cancellation_without_a_reason() {
        Subscription active = pending().activate(CONFIRMED_AT, 30, List.of());

        Subscription cancelled = active.cancel(USER_ID, null, CONFIRMED_AT.plusSeconds(1));

        assertThat(cancelled.getCancellation()).isPresent();
        assertThat(cancelled.getCancellation().get().by()).isEqualTo(USER_ID);
    }

    @Test
    void cancel_rejects_a_blank_or_absent_reason_from_a_non_owner_actor() {
        Subscription active = pending().activate(CONFIRMED_AT, 30, List.of());

        assertThatThrownBy(() -> active.cancel(ADMIN_ID, " ", CONFIRMED_AT))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> active.cancel(ADMIN_ID, null, CONFIRMED_AT))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancel_accepts_a_non_blank_reason_from_a_non_owner_actor() {
        Subscription active = pending().activate(CONFIRMED_AT, 30, List.of());

        Subscription cancelled = active.cancel(ADMIN_ID, "fraud", CONFIRMED_AT);

        assertThat(cancelled.getCancellation()).isPresent();
        assertThat(cancelled.getCancellation().get().reason()).isEqualTo("fraud");
        assertThat(cancelled.getCancellation().get().by()).isEqualTo(ADMIN_ID);
    }

    @Test
    void activate_and_cancelled_transitions_stay_unaffected_by_the_new_cancellation_audit() {
        Subscription activated = pending().activate(CONFIRMED_AT, 30, List.of("course-1"));
        Subscription slotReleased = pending().cancelled();

        assertThat(activated.getCancellation()).isEmpty();
        assertThat(slotReleased.getCancellation()).isEmpty();
        assertThat(slotReleased.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }
}
