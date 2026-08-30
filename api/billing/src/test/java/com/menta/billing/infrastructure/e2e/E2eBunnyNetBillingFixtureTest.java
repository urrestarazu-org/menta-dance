package com.menta.billing.infrastructure.e2e;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.infrastructure.persistence.entity.PlanCourseJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.PlanCourseJpaRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class E2eBunnyNetBillingFixtureTest {

    @Test
    void links_the_planned_course_to_the_mercadopago_plan_when_absent() {
        PlanCourseJpaRepository planCourses = mock(PlanCourseJpaRepository.class);
        when(planCourses.findByPlanId(E2eMercadoPagoBillingFixture.PLAN_ID)).thenReturn(List.of());

        new E2eBunnyNetBillingFixture(planCourses).run(mock());

        verify(planCourses).save(argThat((PlanCourseJpaEntity link) ->
            link.getPlanId().equals(E2eMercadoPagoBillingFixture.PLAN_ID)
                && link.getCourseId().equals(E2eBunnyNetBillingFixture.PLANNED_COURSE_ID)));
    }

    @Test
    void does_not_duplicate_the_link_when_it_already_exists() {
        PlanCourseJpaRepository planCourses = mock(PlanCourseJpaRepository.class);
        PlanCourseJpaEntity existing = new PlanCourseJpaEntity(
            E2eMercadoPagoBillingFixture.PLAN_ID, E2eBunnyNetBillingFixture.PLANNED_COURSE_ID
        );
        when(planCourses.findByPlanId(E2eMercadoPagoBillingFixture.PLAN_ID)).thenReturn(List.of(existing));

        new E2eBunnyNetBillingFixture(planCourses).run(mock());

        verify(planCourses, never()).save(any());
    }
}
