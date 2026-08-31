package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PlanRepository;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentMethod;
import com.menta.billing.domain.model.Plan;
import com.menta.billing.domain.model.PlanCourse;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.domain.model.PlanStatus;
import com.menta.billing.domain.model.FulfillmentStatus;
import com.menta.billing.domain.model.SubscriptionStatus;
import com.menta.billing.domain.model.Subscription;
import com.menta.shared.billing.CourseAccessSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VirtualCourseEntitlementServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-22T12:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();

    private PlanRepository planRepository;
    private SubscriptionRepository subscriptionRepository;
    private VirtualCourseEntitlementService service;

    @BeforeEach
    void setUp() {
        planRepository = mock(PlanRepository.class);
        subscriptionRepository = mock(SubscriptionRepository.class);
        Clock clock = mock(Clock.class);
        when(clock.now()).thenReturn(NOW);
        service = new VirtualCourseEntitlementService(planRepository, subscriptionRepository, clock);
    }

    @Test
    void reports_a_planned_course_and_active_assigned_snapshot_entitlement() {
        when(planRepository.findAllActiveOrderByPriceAsc()).thenReturn(List.of(plan("course-1", "course-2")));
        when(subscriptionRepository.findAllByUserId(USER_ID))
            .thenReturn(List.of(activeSubscription(List.of("course-1", "course-2"))));

        assertThat(service.resolveCourseAccess(USER_ID, "course-2"))
            .isEqualTo(new CourseAccessSnapshot(true, true));
    }

    @Test
    void reports_an_unplanned_course_without_granting_access_by_contract_alone() {
        when(planRepository.findAllActiveOrderByPriceAsc()).thenReturn(List.of(plan("another-course")));
        when(subscriptionRepository.findAllByUserId(USER_ID))
            .thenReturn(List.of(activeSubscription(List.of("course-1"))));

        assertThat(service.resolveCourseAccess(USER_ID, "course-1"))
            .isEqualTo(new CourseAccessSnapshot(false, true));
    }


    @Test
    void grants_a_cancelled_assigned_snapshot_before_its_paid_end_date() {
        when(planRepository.findAllActiveOrderByPriceAsc()).thenReturn(List.of(plan("course-1")));
        when(subscriptionRepository.findAllByUserId(USER_ID))
            .thenReturn(List.of(activeSubscription(List.of("course-1")).cancelled()));

        assertThat(service.resolveCourseAccess(USER_ID, "course-1").currentEntitlement()).isTrue();
    }

    @Test
    void denies_an_entitlement_at_the_end_date_boundary() {
        when(planRepository.findAllActiveOrderByPriceAsc()).thenReturn(List.of(plan("course-1")));
        when(subscriptionRepository.findAllByUserId(USER_ID))
            .thenReturn(List.of(subscription(NOW.minusSeconds(30L * 86_400L), NOW, SubscriptionStatus.ACTIVE,
                FulfillmentStatus.ASSIGNED, List.of("course-1"))));

        assertThat(service.resolveCourseAccess(USER_ID, "course-1").currentEntitlement()).isFalse();
    }

    @Test
    void grants_when_any_of_multiple_snapshots_is_current_and_contains_the_course() {
        when(planRepository.findAllActiveOrderByPriceAsc()).thenReturn(List.of(plan("course-1")));
        when(subscriptionRepository.findAllByUserId(USER_ID)).thenReturn(List.of(
            subscription(NOW.minusSeconds(30L * 86_400L), NOW.plusSeconds(10), SubscriptionStatus.ACTIVE,
                FulfillmentStatus.ASSIGNED, List.of("another-course")),
            activeSubscription(List.of("course-1"))
        ));

        assertThat(service.resolveCourseAccess(USER_ID, "course-1").currentEntitlement()).isTrue();
    }

    @Test
    void denies_pending_exceptional_and_expired_snapshots() {
        when(planRepository.findAllActiveOrderByPriceAsc()).thenReturn(List.of(plan("course-1")));
        when(subscriptionRepository.findAllByUserId(USER_ID)).thenReturn(List.of(
            subscription(NOW, NOW.plusSeconds(10), SubscriptionStatus.PENDING, FulfillmentStatus.ASSIGNED, List.of("course-1")),
            subscription(NOW, NOW.plusSeconds(10), SubscriptionStatus.ACTIVE, FulfillmentStatus.EXCEPTION, List.of("course-1")),
            subscription(NOW, NOW.plusSeconds(10), SubscriptionStatus.EXPIRED, FulfillmentStatus.ASSIGNED, List.of("course-1"))
        ));

        assertThat(service.resolveCourseAccess(USER_ID, "course-1").currentEntitlement()).isFalse();
    }

    @Test
    void anonymous_reads_return_plan_membership_without_querying_subscriptions() {
        when(planRepository.findAllActiveOrderByPriceAsc()).thenReturn(List.of(plan("course-1")));

        assertThat(service.resolveCourseAccess(null, "course-1"))
            .isEqualTo(new CourseAccessSnapshot(true, false));

        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    void invalid_course_queries_return_no_facts_without_accessing_storage() {
        assertThat(service.resolveCourseAccess(USER_ID, " "))
            .isEqualTo(new CourseAccessSnapshot(false, false));

        verifyNoInteractions(planRepository, subscriptionRepository);
    }

    private static Plan plan(String... courseIds) {
        return new Plan(
            PlanId.generate(), "Plan", "Description", Money.of(BigDecimal.TEN, "ARS"), 30, false,
            PlanStatus.ACTIVE, "Terms", "Cancellation", List.of(courseIds).stream().map(PlanCourse::of).toList(),
            Set.of(PaymentMethod.MERCADO_PAGO)
        );
    }

    private static Subscription activeSubscription(List<String> courseIds) {
        return subscription(NOW, NOW.plusSeconds(30L * 86_400L), SubscriptionStatus.ACTIVE,
            FulfillmentStatus.ASSIGNED, courseIds);
    }

    private static Subscription subscription(
        Instant startDate, Instant endDate, SubscriptionStatus status, FulfillmentStatus fulfillmentStatus,
        List<String> courseIds
    ) {
        return new Subscription(
            UUID.randomUUID(), PaymentId.generate(), USER_ID, PlanId.generate(), "idempotency-key", status,
            fulfillmentStatus, startDate, endDate, courseIds, null, null, NOW, null
        );
    }
}
