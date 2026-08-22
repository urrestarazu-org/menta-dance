package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.domain.model.Subscription;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VirtualCourseEntitlementServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-22T12:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();

    private SubscriptionRepository subscriptionRepository;
    private VirtualCourseEntitlementService service;

    @BeforeEach
    void setUp() {
        subscriptionRepository = mock(SubscriptionRepository.class);
        Clock clock = mock(Clock.class);
        when(clock.now()).thenReturn(NOW);
        service = new VirtualCourseEntitlementService(subscriptionRepository, clock);
    }

    @Test
    void grants_entitlement_for_a_course_in_an_active_assigned_snapshot() {
        when(subscriptionRepository.findCurrentByUserId(USER_ID))
            .thenReturn(Optional.of(activeSubscription(List.of("course-1", "course-2"))));

        assertThat(service.hasActiveEntitlement(USER_ID, "course-2")).isTrue();
    }

    @Test
    void denies_entitlement_when_the_subscription_has_expired() {
        Subscription expired = subscriptionAt(NOW.minusSeconds(31L * 86_400L), List.of("course-1"));
        when(subscriptionRepository.findCurrentByUserId(USER_ID)).thenReturn(Optional.of(expired));

        assertThat(service.hasActiveEntitlement(USER_ID, "course-1")).isFalse();
    }

    @Test
    void denies_entitlement_when_snapshot_activation_is_exceptional() {
        Subscription exceptional = Subscription.pendingCheckout(
            UUID.randomUUID(), PaymentId.generate(), USER_ID, PlanId.generate(), "idempotency-key", NOW
        ).activate(NOW, 30, List.of("course-1")).exception();
        when(subscriptionRepository.findCurrentByUserId(USER_ID)).thenReturn(Optional.of(exceptional));

        assertThat(service.hasActiveEntitlement(USER_ID, "course-1")).isFalse();
    }

    @Test
    void rejects_invalid_entitlement_queries_without_accessing_storage() {
        assertThat(service.hasActiveEntitlement(null, "course-1")).isFalse();
        assertThat(service.hasActiveEntitlement(USER_ID, " ")).isFalse();

        verifyNoInteractions(subscriptionRepository);
    }

    private static Subscription activeSubscription(List<String> courseIds) {
        return subscriptionAt(NOW, courseIds);
    }

    private static Subscription subscriptionAt(Instant startDate, List<String> courseIds) {
        return Subscription.pendingCheckout(
            UUID.randomUUID(), PaymentId.generate(), USER_ID, PlanId.generate(), "idempotency-key", startDate
        ).activate(startDate, 30, courseIds).assigned();
    }
}
