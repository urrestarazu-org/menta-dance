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
import com.menta.billing.domain.model.Subscription;
import com.menta.shared.billing.CourseAccessSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
        when(subscriptionRepository.findCurrentByUserId(USER_ID))
            .thenReturn(Optional.of(activeSubscription(List.of("course-1", "course-2"))));

        assertThat(service.resolveCourseAccess(USER_ID, "course-2"))
            .isEqualTo(new CourseAccessSnapshot(true, true));
    }

    @Test
    void reports_an_unplanned_course_without_granting_access_by_contract_alone() {
        when(planRepository.findAllActiveOrderByPriceAsc()).thenReturn(List.of(plan("another-course")));
        when(subscriptionRepository.findCurrentByUserId(USER_ID))
            .thenReturn(Optional.of(activeSubscription(List.of("course-1"))));

        assertThat(service.resolveCourseAccess(USER_ID, "course-1"))
            .isEqualTo(new CourseAccessSnapshot(false, true));
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
        return Subscription.pendingCheckout(
            UUID.randomUUID(), PaymentId.generate(), USER_ID, PlanId.generate(), "idempotency-key", NOW
        ).activate(NOW, 30, courseIds).assigned();
    }
}
