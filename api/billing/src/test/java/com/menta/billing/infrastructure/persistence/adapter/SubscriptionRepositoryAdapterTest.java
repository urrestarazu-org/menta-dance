package com.menta.billing.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.domain.exception.SubscriptionAlreadyActiveException;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.domain.model.Subscription;
import com.menta.billing.domain.model.TrialGrant;
import com.menta.billing.infrastructure.persistence.entity.SubscriptionCourseJpaEntity;
import com.menta.billing.infrastructure.persistence.mapper.SubscriptionJpaMapper;
import com.menta.billing.infrastructure.persistence.repository.SubscriptionCourseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.SubscriptionJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class SubscriptionRepositoryAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();

    private SubscriptionJpaRepository jpaRepository;
    private SubscriptionCourseJpaRepository courseJpaRepository;
    private SubscriptionRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        jpaRepository = mock(SubscriptionJpaRepository.class);
        courseJpaRepository = mock(SubscriptionCourseJpaRepository.class);
        when(jpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(courseJpaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        adapter = new SubscriptionRepositoryAdapter(jpaRepository, courseJpaRepository);
    }

    private static Subscription pending() {
        return Subscription.pendingCheckout(
            UUID.randomUUID(), PaymentId.generate(), USER_ID, PlanId.generate(), "idem-1", NOW
        );
    }

    @Test
    void save_maps_domain_to_entity_and_back() {
        Subscription subscription = pending();

        Subscription saved = adapter.save(subscription);

        assertThat(saved.getId()).isEqualTo(subscription.getId());
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
    }

    @Test
    void save_replaces_the_course_snapshot_wholesale() {
        Subscription activated = pending().activate(NOW, 30, List.of("course-1", "course-2"));

        Subscription saved = adapter.save(activated);

        verify(courseJpaRepository).deleteBySubscriptionId(activated.getId());
        verify(courseJpaRepository).saveAll(any());
        assertThat(saved.getCourseIds()).containsExactly("course-1", "course-2");
    }

    @Test
    void saveNewCheckout_returns_the_persisted_subscription_with_no_courses() {
        Subscription subscription = pending();

        Subscription saved = adapter.saveNewCheckout(subscription);

        assertThat(saved.getId()).isEqualTo(subscription.getId());
        assertThat(saved.getCourseIds()).isEmpty();
        verify(jpaRepository).flush();
    }

    /** The unique index is the mechanism — the adapter only translates it, and never swallows it. */
    @Test
    void saveNewCheckout_translates_a_unique_violation_into_a_domain_conflict() {
        doThrow(new DataIntegrityViolationException("uq_billing_subscriptions_active_user"))
            .when(jpaRepository).flush();

        assertThatThrownBy(() -> adapter.saveNewCheckout(pending()))
            .isInstanceOf(SubscriptionAlreadyActiveException.class);
        verify(courseJpaRepository, never()).saveAll(any());
    }

    /**
     * Regression guard (A12): {@code saveNewCheckout} must keep persisting zero courses — its
     * snapshot is written later, at activation. Reusing it for a trial would silently ship a
     * subscription with no enabled courses.
     */
    @Test
    void saveNewCheckout_persists_zero_courses_guarding_against_reuse_for_a_trial() {
        adapter.saveNewCheckout(pending());

        verify(courseJpaRepository, never()).saveAll(any());
        verify(courseJpaRepository, never()).deleteBySubscriptionId(any());
    }

    private static Subscription trialSubscription() {
        return Subscription.trial(
            UUID.randomUUID(), USER_ID, PlanId.generate(), NOW, 14, List.of("course-1", "course-2"),
            new TrialGrant(NOW, UUID.randomUUID(), "evaluación de producto", 14)
        );
    }

    /** A12 regression lock: reusing saveNewCheckout for a trial would silently persist zero courses. */
    @Test
    void saveNewSubscription_persists_the_full_course_snapshot_atomically() {
        Subscription trial = trialSubscription();

        Subscription saved = adapter.saveNewSubscription(trial);

        verify(courseJpaRepository).deleteBySubscriptionId(trial.getId());
        verify(courseJpaRepository).saveAll(any());
        verify(jpaRepository).flush();
        assertThat(saved.getCourseIds()).containsExactly("course-1", "course-2");
    }

    @Test
    void saveNewSubscription_translates_a_slot_violation_into_a_domain_conflict() {
        doThrow(new DataIntegrityViolationException("uq_billing_subscriptions_active_user"))
            .when(jpaRepository).flush();

        assertThatThrownBy(() -> adapter.saveNewSubscription(trialSubscription()))
            .isInstanceOf(SubscriptionAlreadyActiveException.class);
        verify(courseJpaRepository, never()).saveAll(any());
    }

    @Test
    void findExpirableIds_delegates_to_the_id_projection_query() {
        UUID expirableId = UUID.randomUUID();
        when(jpaRepository.findExpirableIds(eq(NOW), any())).thenReturn(List.of(expirableId));

        List<UUID> found = adapter.findExpirableIds(NOW, 50);

        assertThat(found).containsExactly(expirableId);
    }

    @Test
    void findByPaymentId_maps_when_present_including_its_snapshot() {
        Subscription subscription = pending().activate(NOW, 30, List.of("course-1"));
        when(jpaRepository.findByPaymentId(subscription.getPaymentId().orElseThrow().getValue()))
            .thenReturn(Optional.of(SubscriptionJpaMapper.toEntity(subscription)));
        when(courseJpaRepository.findBySubscriptionId(subscription.getId()))
            .thenReturn(List.of(new SubscriptionCourseJpaEntity(subscription.getId(), "course-1")));

        Optional<Subscription> found = adapter.findByPaymentId(subscription.getPaymentId().orElseThrow());

        assertThat(found).isPresent();
        assertThat(found.get().getCourseIds()).containsExactly("course-1");
    }

    @Test
    void findByPaymentId_empty_when_absent() {
        PaymentId paymentId = PaymentId.generate();
        when(jpaRepository.findByPaymentId(paymentId.getValue())).thenReturn(Optional.empty());

        assertThat(adapter.findByPaymentId(paymentId)).isEmpty();
    }

    @Test
    void findByUserIdAndIdempotencyKey_maps_when_present() {
        Subscription subscription = pending();
        when(jpaRepository.findByUserIdAndIdempotencyKey(USER_ID, "idem-1"))
            .thenReturn(Optional.of(SubscriptionJpaMapper.toEntity(subscription)));
        when(courseJpaRepository.findBySubscriptionId(subscription.getId())).thenReturn(List.of());

        assertThat(adapter.findByUserIdAndIdempotencyKey(USER_ID, "idem-1")).isPresent();
    }

    @Test
    void findByUserIdAndIdempotencyKey_empty_when_absent() {
        when(jpaRepository.findByUserIdAndIdempotencyKey(USER_ID, "other")).thenReturn(Optional.empty());

        assertThat(adapter.findByUserIdAndIdempotencyKey(USER_ID, "other")).isEmpty();
    }

    /** The slot lookup goes through active_user_id — the same column the unique index sits on. */
    @Test
    void findCurrentByUserId_resolves_through_the_active_user_column() {
        Subscription subscription = pending();
        when(jpaRepository.findByActiveUserId(USER_ID))
            .thenReturn(Optional.of(SubscriptionJpaMapper.toEntity(subscription)));
        when(courseJpaRepository.findBySubscriptionId(subscription.getId())).thenReturn(List.of());

        assertThat(adapter.findCurrentByUserId(USER_ID)).isPresent();
    }

    @Test
    void findAllByUserId_maps_every_subscription_with_its_course_snapshot() {
        Subscription subscription = pending().activate(NOW, 30, List.of("course-1"));
        when(jpaRepository.findAllByUserId(USER_ID)).thenReturn(List.of(SubscriptionJpaMapper.toEntity(subscription)));
        when(courseJpaRepository.findBySubscriptionId(subscription.getId())).thenReturn(List.of(
            new SubscriptionCourseJpaEntity(subscription.getId(), "course-1")
        ));

        assertThat(adapter.findAllByUserId(USER_ID)).singleElement()
            .satisfies(found -> assertThat(found.getCourseIds()).containsExactly("course-1"));
    }

    @Test
    void findCurrentByUserId_empty_when_the_slot_is_free() {
        when(jpaRepository.findByActiveUserId(USER_ID)).thenReturn(Optional.empty());

        assertThat(adapter.findCurrentByUserId(USER_ID)).isEmpty();
    }

    /** Strictly ACTIVE, unlike {@code findCurrentByUserId} which also matches PENDING (US-BILLING-011). */
    @Test
    void findActiveByUserId_maps_when_an_active_subscription_exists() {
        Subscription subscription = pending().activate(NOW, 30, List.of("course-1"));
        when(jpaRepository.findByUserIdAndStatus(USER_ID, "ACTIVE"))
            .thenReturn(Optional.of(SubscriptionJpaMapper.toEntity(subscription)));
        when(courseJpaRepository.findBySubscriptionId(subscription.getId()))
            .thenReturn(List.of(new SubscriptionCourseJpaEntity(subscription.getId(), "course-1")));

        Optional<Subscription> found = adapter.findActiveByUserId(USER_ID);

        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(com.menta.billing.domain.model.SubscriptionStatus.ACTIVE);
        assertThat(found.get().getCourseIds()).containsExactly("course-1");
    }

    @Test
    void findActiveByUserId_empty_when_no_active_subscription_exists() {
        when(jpaRepository.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(Optional.empty());

        assertThat(adapter.findActiveByUserId(USER_ID)).isEmpty();
    }

    @Test
    void findById_maps_when_present_including_its_snapshot() {
        Subscription subscription = pending().activate(NOW, 30, List.of("course-1"));
        when(jpaRepository.findById(subscription.getId()))
            .thenReturn(Optional.of(SubscriptionJpaMapper.toEntity(subscription)));
        when(courseJpaRepository.findBySubscriptionId(subscription.getId()))
            .thenReturn(List.of(new SubscriptionCourseJpaEntity(subscription.getId(), "course-1")));

        Optional<Subscription> found = adapter.findById(subscription.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(subscription.getId());
        assertThat(found.get().getCourseIds()).containsExactly("course-1");
    }

    @Test
    void findById_empty_when_absent() {
        UUID subscriptionId = UUID.randomUUID();
        when(jpaRepository.findById(subscriptionId)).thenReturn(Optional.empty());

        assertThat(adapter.findById(subscriptionId)).isEmpty();
    }

    /**
     * D3: the derived query is the whole implementation, so this test only proves the adapter
     * wires the right column values through — not the ordering/filtering itself (that lives in
     * the Spring Data method name and is proven at the integration layer, task 5.12).
     */
    @Test
    void findLatestCancelledWithRemainingAccess_maps_when_a_matching_row_exists() {
        PlanId planId = PlanId.generate();
        Instant at = NOW;
        Subscription cancelled = pending().activate(NOW, 30, List.of("course-1"))
            .cancel(USER_ID, null, NOW);
        when(jpaRepository.findFirstByUserIdAndPlanIdAndStatusAndEndDateAfterOrderByEndDateDesc(
            USER_ID, planId.getValue(), "CANCELLED", at
        )).thenReturn(Optional.of(SubscriptionJpaMapper.toEntity(cancelled)));
        when(courseJpaRepository.findBySubscriptionId(cancelled.getId()))
            .thenReturn(List.of(new SubscriptionCourseJpaEntity(cancelled.getId(), "course-1")));

        Optional<Subscription> found = adapter.findLatestCancelledWithRemainingAccess(USER_ID, planId, at);

        assertThat(found).isPresent();
        assertThat(found.get().getStatus())
            .isEqualTo(com.menta.billing.domain.model.SubscriptionStatus.CANCELLED);
    }

    @Test
    void findLatestCancelledWithRemainingAccess_empty_when_no_matching_row_exists() {
        PlanId planId = PlanId.generate();
        Instant at = NOW;
        when(jpaRepository.findFirstByUserIdAndPlanIdAndStatusAndEndDateAfterOrderByEndDateDesc(
            USER_ID, planId.getValue(), "CANCELLED", at
        )).thenReturn(Optional.empty());

        assertThat(adapter.findLatestCancelledWithRemainingAccess(USER_ID, planId, at)).isEmpty();
    }
}
