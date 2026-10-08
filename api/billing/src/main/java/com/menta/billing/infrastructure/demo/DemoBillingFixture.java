package com.menta.billing.infrastructure.demo;

import com.menta.billing.application.dto.AssignTrialCommand;
import com.menta.billing.application.dto.UpdatePhysicalCoursePricingCommand;
import com.menta.billing.application.port.in.AssignTrialSubscriptionUseCase;
import com.menta.billing.application.port.in.UpdatePhysicalCoursePricingUseCase;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PhysicalCoursePricingRepository;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.domain.model.PlanStatus;
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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * Seeds the demo Billing data: the two plans with their payment methods and courses, the physical course
 * prices and the student's trial subscription.
 *
 * <p>Every step is idempotent per entity: a plan is created only when its fixed id is absent, links are
 * compared against {@code findByPlanId} before each save, pricing is published only when the course has none
 * and the trial is assigned only when the student has no current subscription. Pricing and the trial go
 * through the real use cases, so they keep their audit trail and rules.
 *
 * <p>The runner is intentionally not transactional and swallows nothing: the use cases open their own
 * transactions and any failure aborts the startup. Courses and the student are created by earlier runners
 * ({@link DemoSeedOrder#CONTENT}, {@link DemoSeedOrder#AUTH}); Billing only refers to them through
 * {@link DemoIds}.
 */
@Component
@Profile("demo")
@RequiredArgsConstructor
public final class DemoBillingFixture implements ApplicationRunner, Ordered {

    static final UUID PLAN_VIRTUAL_ID = UUID.fromString("de000000-0000-4000-8000-0000000006a1");
    static final UUID PLAN_COMPLETE_ID = UUID.fromString("de000000-0000-4000-8000-0000000006a2");

    private static final String CURRENCY = "ARS";
    private static final String REASON = "Demo seed";
    private static final int PLAN_DURATION_DAYS = 30;
    private static final int TRIAL_DAYS = 90;
    private static final BigDecimal INDIVIDUAL_SURCHARGE_PERCENT = new BigDecimal("20");
    private static final List<String> PAYMENT_METHODS = List.of("MERCADO_PAGO", "BANK_TRANSFER");

    private static final DemoPlan VIRTUAL_PLAN = new DemoPlan(
        PLAN_VIRTUAL_ID, "Plan Virtual", "Access to every online course.", new BigDecimal("30000"),
        List.of(DemoIds.VIRTUAL_SENSUAL_COURSE_ID, DemoIds.VIRTUAL_TRADITIONAL_COURSE_ID));
    private static final DemoPlan COMPLETE_PLAN = new DemoPlan(
        PLAN_COMPLETE_ID, "Plan Completo", "Access to every online and in-person course.",
        new BigDecimal("60000"),
        List.of(
            DemoIds.VIRTUAL_SENSUAL_COURSE_ID, DemoIds.VIRTUAL_TRADITIONAL_COURSE_ID,
            DemoIds.PHYSICAL_SENSUAL_COURSE_ID, DemoIds.PHYSICAL_TRADITIONAL_COURSE_ID));

    private final PlanJpaRepository planRepository;
    private final PlanPaymentMethodJpaRepository paymentMethodRepository;
    private final PlanCourseJpaRepository planCourseRepository;
    private final PhysicalCoursePricingRepository pricingRepository;
    private final UpdatePhysicalCoursePricingUseCase pricingUseCase;
    private final SubscriptionRepository subscriptionRepository;
    private final AssignTrialSubscriptionUseCase trialUseCase;
    private final Clock clock;

    @Override
    public void run(ApplicationArguments arguments) {
        seedPlan(VIRTUAL_PLAN);
        seedPlan(COMPLETE_PLAN);
        seedPhysicalPricing(DemoIds.PHYSICAL_SENSUAL_COURSE_ID, new BigDecimal("25000"));
        seedPhysicalPricing(DemoIds.PHYSICAL_TRADITIONAL_COURSE_ID, new BigDecimal("20000"));
        seedTrial();
    }

    @Override
    public int getOrder() {
        return DemoSeedOrder.BILLING;
    }

    private void seedPlan(DemoPlan plan) {
        if (!planRepository.existsById(plan.id())) {
            Instant now = clock.now();
            planRepository.save(new PlanJpaEntity(
                plan.id(), plan.name(), plan.description(), plan.price(), CURRENCY, PLAN_DURATION_DAYS, false,
                PlanStatus.ACTIVE, "Términos de la demo.", "Política de cancelación de la demo.", now, now));
        }
        List<String> methods = paymentMethodRepository.findByPlanId(plan.id()).stream()
            .map(PlanPaymentMethodJpaEntity::getPaymentMethod).toList();
        PAYMENT_METHODS.stream().filter(method -> !methods.contains(method))
            .forEach(method -> paymentMethodRepository.save(new PlanPaymentMethodJpaEntity(plan.id(), method)));
        List<String> courses = planCourseRepository.findByPlanId(plan.id()).stream()
            .map(PlanCourseJpaEntity::getCourseId).toList();
        plan.courseIds().stream().map(UUID::toString).filter(course -> !courses.contains(course))
            .forEach(course -> planCourseRepository.save(new PlanCourseJpaEntity(plan.id(), course)));
    }

    private void seedPhysicalPricing(UUID courseId, BigDecimal monthlyPrice) {
        String id = courseId.toString();
        if (pricingRepository.findByCourseId(id).isEmpty()) {
            UpdatePhysicalCoursePricingCommand command = new UpdatePhysicalCoursePricingCommand(
                monthlyPrice, CURRENCY, INDIVIDUAL_SURCHARGE_PERCENT, REASON);
            pricingUseCase.update(id, command, DemoIds.ADMIN_USER_ID, true);
        }
    }

    private void seedTrial() {
        if (subscriptionRepository.findCurrentByUserId(DemoIds.STUDENT_USER_ID).isEmpty()) {
            trialUseCase.assign(new AssignTrialCommand(
                DemoIds.STUDENT_USER_ID, PLAN_COMPLETE_ID.toString(), DemoIds.ADMIN_USER_ID, true, REASON,
                TRIAL_DAYS));
        }
    }

    private record DemoPlan(UUID id, String name, String description, BigDecimal price, List<UUID> courseIds) {
    }
}
