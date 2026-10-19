package com.menta.billing.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.AssignTrialCommand;
import com.menta.billing.application.dto.UpdatePhysicalCoursePricingCommand;
import com.menta.billing.application.port.in.AssignTrialSubscriptionUseCase;
import com.menta.billing.application.port.in.UpdatePhysicalCoursePricingUseCase;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PhysicalCoursePricingRepository;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.domain.model.PhysicalCoursePricing;
import com.menta.billing.domain.model.PlanStatus;
import com.menta.billing.domain.model.Subscription;
import com.menta.billing.infrastructure.persistence.entity.PlanCourseJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PlanJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PlanPaymentMethodJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.PlanCourseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PlanJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PlanPaymentMethodJpaRepository;
import com.menta.shared.demo.DemoIds;
import com.menta.shared.demo.DemoSeedOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DemoBillingFixtureTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    private final PlanJpaRepository plans = mock(PlanJpaRepository.class);
    private final PlanPaymentMethodJpaRepository methods = mock(PlanPaymentMethodJpaRepository.class);
    private final PlanCourseJpaRepository planCourses = mock(PlanCourseJpaRepository.class);
    private final PhysicalCoursePricingRepository pricing = mock(PhysicalCoursePricingRepository.class);
    private final UpdatePhysicalCoursePricingUseCase pricingUseCase = mock(UpdatePhysicalCoursePricingUseCase.class);
    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final AssignTrialSubscriptionUseCase trialUseCase = mock(AssignTrialSubscriptionUseCase.class);
    private final Clock clock = mock(Clock.class);

    private DemoBillingFixture fixture;

    @BeforeEach
    void setUp() {
        when(clock.now()).thenReturn(NOW);
        when(methods.findByPlanId(any())).thenReturn(List.of());
        when(planCourses.findByPlanId(any())).thenReturn(List.of());
        when(pricing.findByCourseId(any())).thenReturn(Optional.empty());
        when(subscriptions.findCurrentByUserId(DemoIds.STUDENT_USER_ID)).thenReturn(Optional.empty());
        fixture = new DemoBillingFixture(
            plans, methods, planCourses, pricing, pricingUseCase, subscriptions, trialUseCase, clock);
    }

    @Test
    void creates_both_plans_with_approved_price_links_and_payment_methods_when_absent() {
        fixture.run(mock());

        ArgumentCaptor<PlanJpaEntity> saved = ArgumentCaptor.forClass(PlanJpaEntity.class);
        verify(plans, org.mockito.Mockito.times(2)).save(saved.capture());
        PlanJpaEntity virtual = saved.getAllValues().get(0);
        PlanJpaEntity complete = saved.getAllValues().get(1);
        assertThat(virtual.getId()).isEqualTo(DemoBillingFixture.PLAN_VIRTUAL_ID);
        assertThat(virtual.getName()).isEqualTo("Plan Virtual");
        assertThat(virtual.getPrice()).isEqualByComparingTo("30000");
        assertThat(virtual.getCurrency()).isEqualTo("ARS");
        assertThat(virtual.getDurationDays()).isEqualTo(30);
        assertThat(virtual.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(virtual.getCreatedAt()).isEqualTo(NOW);
        assertThat(complete.getId()).isEqualTo(DemoBillingFixture.PLAN_COMPLETE_ID);
        assertThat(complete.getName()).isEqualTo("Plan Completo");
        assertThat(complete.getPrice()).isEqualByComparingTo("60000");
        assertThat(complete.getDurationDays()).isEqualTo(30);

        ArgumentCaptor<PlanPaymentMethodJpaEntity> method = ArgumentCaptor.forClass(PlanPaymentMethodJpaEntity.class);
        verify(methods, org.mockito.Mockito.times(4)).save(method.capture());
        assertThat(method.getAllValues())
            .extracting(PlanPaymentMethodJpaEntity::getPlanId, PlanPaymentMethodJpaEntity::getPaymentMethod)
            .containsExactly(
                org.assertj.core.api.Assertions.tuple(DemoBillingFixture.PLAN_VIRTUAL_ID, "MERCADO_PAGO"),
                org.assertj.core.api.Assertions.tuple(DemoBillingFixture.PLAN_VIRTUAL_ID, "BANK_TRANSFER"),
                org.assertj.core.api.Assertions.tuple(DemoBillingFixture.PLAN_COMPLETE_ID, "MERCADO_PAGO"),
                org.assertj.core.api.Assertions.tuple(DemoBillingFixture.PLAN_COMPLETE_ID, "BANK_TRANSFER"));

        ArgumentCaptor<PlanCourseJpaEntity> course = ArgumentCaptor.forClass(PlanCourseJpaEntity.class);
        verify(planCourses, org.mockito.Mockito.times(6)).save(course.capture());
        assertThat(course.getAllValues())
            .extracting(PlanCourseJpaEntity::getPlanId, PlanCourseJpaEntity::getCourseId)
            .containsExactly(
                org.assertj.core.api.Assertions.tuple(
                    DemoBillingFixture.PLAN_VIRTUAL_ID, DemoIds.VIRTUAL_SENSUAL_COURSE_ID.toString()),
                org.assertj.core.api.Assertions.tuple(
                    DemoBillingFixture.PLAN_VIRTUAL_ID, DemoIds.VIRTUAL_TRADITIONAL_COURSE_ID.toString()),
                org.assertj.core.api.Assertions.tuple(
                    DemoBillingFixture.PLAN_COMPLETE_ID, DemoIds.VIRTUAL_SENSUAL_COURSE_ID.toString()),
                org.assertj.core.api.Assertions.tuple(
                    DemoBillingFixture.PLAN_COMPLETE_ID, DemoIds.VIRTUAL_TRADITIONAL_COURSE_ID.toString()),
                org.assertj.core.api.Assertions.tuple(
                    DemoBillingFixture.PLAN_COMPLETE_ID, DemoIds.PHYSICAL_SENSUAL_COURSE_ID.toString()),
                org.assertj.core.api.Assertions.tuple(
                    DemoBillingFixture.PLAN_COMPLETE_ID, DemoIds.PHYSICAL_TRADITIONAL_COURSE_ID.toString()));
    }

    @Test
    void creates_nothing_for_plans_when_plans_and_links_already_exist() {
        when(plans.existsById(any())).thenReturn(true);
        when(methods.findByPlanId(any())).thenAnswer(invocation -> List.of(
            new PlanPaymentMethodJpaEntity(invocation.getArgument(0), "MERCADO_PAGO"),
            new PlanPaymentMethodJpaEntity(invocation.getArgument(0), "BANK_TRANSFER")));
        when(planCourses.findByPlanId(DemoBillingFixture.PLAN_VIRTUAL_ID)).thenReturn(List.of(
            link(DemoBillingFixture.PLAN_VIRTUAL_ID, DemoIds.VIRTUAL_SENSUAL_COURSE_ID),
            link(DemoBillingFixture.PLAN_VIRTUAL_ID, DemoIds.VIRTUAL_TRADITIONAL_COURSE_ID)));
        when(planCourses.findByPlanId(DemoBillingFixture.PLAN_COMPLETE_ID)).thenReturn(List.of(
            link(DemoBillingFixture.PLAN_COMPLETE_ID, DemoIds.VIRTUAL_SENSUAL_COURSE_ID),
            link(DemoBillingFixture.PLAN_COMPLETE_ID, DemoIds.VIRTUAL_TRADITIONAL_COURSE_ID),
            link(DemoBillingFixture.PLAN_COMPLETE_ID, DemoIds.PHYSICAL_SENSUAL_COURSE_ID),
            link(DemoBillingFixture.PLAN_COMPLETE_ID, DemoIds.PHYSICAL_TRADITIONAL_COURSE_ID)));

        fixture.run(mock());

        verify(plans, never()).save(any());
        verify(methods, never()).save(any());
        verify(planCourses, never()).save(any());
    }

    @Test
    void adds_only_the_missing_link_and_payment_method_to_an_existing_plan() {
        when(plans.existsById(any())).thenReturn(true);
        when(methods.findByPlanId(any())).thenAnswer(invocation -> List.of(
            new PlanPaymentMethodJpaEntity(invocation.getArgument(0), "MERCADO_PAGO")));
        when(planCourses.findByPlanId(DemoBillingFixture.PLAN_VIRTUAL_ID)).thenReturn(List.of(
            link(DemoBillingFixture.PLAN_VIRTUAL_ID, DemoIds.VIRTUAL_SENSUAL_COURSE_ID)));
        when(planCourses.findByPlanId(DemoBillingFixture.PLAN_COMPLETE_ID)).thenReturn(List.of(
            link(DemoBillingFixture.PLAN_COMPLETE_ID, DemoIds.VIRTUAL_SENSUAL_COURSE_ID),
            link(DemoBillingFixture.PLAN_COMPLETE_ID, DemoIds.VIRTUAL_TRADITIONAL_COURSE_ID),
            link(DemoBillingFixture.PLAN_COMPLETE_ID, DemoIds.PHYSICAL_SENSUAL_COURSE_ID),
            link(DemoBillingFixture.PLAN_COMPLETE_ID, DemoIds.PHYSICAL_TRADITIONAL_COURSE_ID)));

        fixture.run(mock());

        verify(plans, never()).save(any());
        verify(methods, org.mockito.Mockito.times(2)).save(any());
        ArgumentCaptor<PlanCourseJpaEntity> course = ArgumentCaptor.forClass(PlanCourseJpaEntity.class);
        verify(planCourses).save(course.capture());
        assertThat(course.getValue().getPlanId()).isEqualTo(DemoBillingFixture.PLAN_VIRTUAL_ID);
        assertThat(course.getValue().getCourseId()).isEqualTo(DemoIds.VIRTUAL_TRADITIONAL_COURSE_ID.toString());
    }

    @Test
    void publishes_physical_pricing_with_individual_surcharge_when_absent() {
        fixture.run(mock());

        ArgumentCaptor<UpdatePhysicalCoursePricingCommand> sensual =
            ArgumentCaptor.forClass(UpdatePhysicalCoursePricingCommand.class);
        verify(pricingUseCase).update(
            eq(DemoIds.PHYSICAL_SENSUAL_COURSE_ID.toString()), sensual.capture(),
            eq(DemoIds.ADMIN_USER_ID), eq(true));
        assertThat(sensual.getValue().monthlyPrice()).isEqualByComparingTo("25000");
        assertThat(sensual.getValue().currency()).isEqualTo("ARS");
        assertThat(sensual.getValue().individualSurchargePercent()).isEqualByComparingTo(new BigDecimal("20"));
        assertThat(sensual.getValue().reason()).isEqualTo("Demo seed");
        ArgumentCaptor<UpdatePhysicalCoursePricingCommand> traditional =
            ArgumentCaptor.forClass(UpdatePhysicalCoursePricingCommand.class);
        verify(pricingUseCase).update(
            eq(DemoIds.PHYSICAL_TRADITIONAL_COURSE_ID.toString()), traditional.capture(),
            eq(DemoIds.ADMIN_USER_ID), eq(true));
        assertThat(traditional.getValue().monthlyPrice()).isEqualByComparingTo("20000");
    }

    @Test
    void does_not_republish_pricing_that_already_exists() {
        when(pricing.findByCourseId(any())).thenReturn(Optional.of(mock(PhysicalCoursePricing.class)));

        fixture.run(mock());

        verify(pricingUseCase, never()).update(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void assigns_a_ninety_day_trial_on_the_complete_plan_when_the_student_has_no_subscription() {
        fixture.run(mock());

        verify(trialUseCase).assign(new AssignTrialCommand(
            DemoIds.STUDENT_USER_ID, DemoBillingFixture.PLAN_COMPLETE_ID.toString(), DemoIds.ADMIN_USER_ID,
            true, "Demo seed", 90));
    }

    @Test
    void does_not_assign_a_trial_when_the_student_already_has_a_current_subscription() {
        when(subscriptions.findCurrentByUserId(DemoIds.STUDENT_USER_ID))
            .thenReturn(Optional.of(mock(Subscription.class)));

        fixture.run(mock());

        verify(trialUseCase, never()).assign(any());
    }

    @Test
    void runs_in_the_billing_slot_after_content() {
        assertThat(fixture.getOrder()).isEqualTo(DemoSeedOrder.BILLING);
    }

    private static PlanCourseJpaEntity link(UUID planId, UUID courseId) {
        return new PlanCourseJpaEntity(planId, courseId.toString());
    }
}
