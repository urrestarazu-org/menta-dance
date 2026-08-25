package com.menta.billing.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.domain.exception.SubscriptionAlreadyActiveException;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.domain.model.Subscription;
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

    @Test
    void findByPaymentId_maps_when_present_including_its_snapshot() {
        Subscription subscription = pending().activate(NOW, 30, List.of("course-1"));
        when(jpaRepository.findByPaymentId(subscription.getPaymentId().getValue()))
            .thenReturn(Optional.of(SubscriptionJpaMapper.toEntity(subscription)));
        when(courseJpaRepository.findBySubscriptionId(subscription.getId()))
            .thenReturn(List.of(new SubscriptionCourseJpaEntity(subscription.getId(), "course-1")));

        Optional<Subscription> found = adapter.findByPaymentId(subscription.getPaymentId());

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
}
