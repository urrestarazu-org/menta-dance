package com.menta.billing.infrastructure.e2e;

import com.menta.billing.infrastructure.persistence.entity.PlanCourseJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.PlanCourseJpaRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * Links the local Bunny.net journey's planned course to the Mercado Pago fixture plan
 * (design.md A4').
 *
 * <p>Per the D7 scope reduction this fixture is gated by BOTH {@code e2e-bunny-net} and
 * {@code e2e-mercadopago}: the denial scenario is fully covered by {@code
 * E2eBunnyNetVirtualFixture}'s unplanned course alone, so this link is only needed to compose
 * the premium-grant scenario with the Mercado Pago checkout/webhook flow.</p>
 */
@Component
@Profile("e2e-bunny-net & e2e-mercadopago")
public final class E2eBunnyNetBillingFixture implements ApplicationRunner, Ordered {

    /**
     * Mirrors {@code E2eBunnyNetVirtualFixture.PLANNED_COURSE_ID}. Billing cannot depend on
     * Virtual, so the UUID literal is duplicated here on purpose (design.md A4').
     */
    public static final String PLANNED_COURSE_ID = "00000000-0000-0000-0000-000000000130";

    private final PlanCourseJpaRepository planCourseRepository;

    public E2eBunnyNetBillingFixture(PlanCourseJpaRepository planCourseRepository) {
        this.planCourseRepository = planCourseRepository;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        boolean alreadyLinked = planCourseRepository.findByPlanId(E2eMercadoPagoBillingFixture.PLAN_ID).stream()
            .anyMatch(link -> link.getCourseId().equals(PLANNED_COURSE_ID));
        if (alreadyLinked) {
            return;
        }
        planCourseRepository.save(new PlanCourseJpaEntity(E2eMercadoPagoBillingFixture.PLAN_ID, PLANNED_COURSE_ID));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
