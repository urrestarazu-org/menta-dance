package com.menta.billing.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.billing.domain.model.PaymentMethod;
import com.menta.billing.domain.model.Plan;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.domain.model.PlanStatus;
import com.menta.billing.infrastructure.persistence.entity.PlanCourseJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PlanJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PlanPaymentMethodJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.PlanCourseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PlanJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PlanPaymentMethodJpaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlanRepositoryAdapterTest {

    private PlanJpaRepository planJpaRepository;
    private PlanCourseJpaRepository planCourseJpaRepository;
    private PlanPaymentMethodJpaRepository planPaymentMethodJpaRepository;
    private PlanRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        planJpaRepository = mock(PlanJpaRepository.class);
        planCourseJpaRepository = mock(PlanCourseJpaRepository.class);
        planPaymentMethodJpaRepository = mock(PlanPaymentMethodJpaRepository.class);
        adapter = new PlanRepositoryAdapter(
            planJpaRepository, planCourseJpaRepository, planPaymentMethodJpaRepository
        );
    }

    private static PlanJpaEntity plan(UUID id) {
        Instant now = Instant.now();
        return new PlanJpaEntity(
            id, "Plan Mensual", "Acceso mensual", BigDecimal.valueOf(19.99), "ARS",
            30, true, PlanStatus.ACTIVE, "Términos", "Política de cancelación", now, now
        );
    }

    @Test
    void findAllActiveOrderByPriceAsc_returns_empty_when_there_are_no_plans() {
        when(planJpaRepository.findAllByStatusOrderByPriceAsc(PlanStatus.ACTIVE)).thenReturn(List.of());

        List<Plan> plans = adapter.findAllActiveOrderByPriceAsc();

        assertThat(plans).isEmpty();
    }

    @Test
    void findAllActiveOrderByPriceAsc_joins_plans_with_their_courses_in_memory() {
        UUID planId = UUID.randomUUID();
        PlanJpaEntity entity = plan(planId);
        when(planJpaRepository.findAllByStatusOrderByPriceAsc(PlanStatus.ACTIVE)).thenReturn(List.of(entity));
        when(planCourseJpaRepository.findByPlanIdIn(List.of(planId)))
            .thenReturn(List.of(new PlanCourseJpaEntity(planId, "course-1")));

        List<Plan> plans = adapter.findAllActiveOrderByPriceAsc();

        assertThat(plans).hasSize(1);
        assertThat(plans.get(0).getId().getValue()).isEqualTo(planId);
        assertThat(plans.get(0).getCourses()).extracting(course -> course.getCourseId())
            .containsExactly("course-1");
    }

    @Test
    void findAllActiveOrderByPriceAsc_defaults_to_no_courses_when_none_match() {
        UUID planId = UUID.randomUUID();
        PlanJpaEntity entity = plan(planId);
        when(planJpaRepository.findAllByStatusOrderByPriceAsc(PlanStatus.ACTIVE)).thenReturn(List.of(entity));
        when(planCourseJpaRepository.findByPlanIdIn(List.of(planId))).thenReturn(List.of());

        List<Plan> plans = adapter.findAllActiveOrderByPriceAsc();

        assertThat(plans.get(0).getCourses()).isEmpty();
    }

    @Test
    void findActiveById_maps_when_present() {
        UUID planId = UUID.randomUUID();
        PlanId id = PlanId.of(planId);
        PlanJpaEntity entity = plan(planId);
        when(planJpaRepository.findByIdAndStatus(planId, PlanStatus.ACTIVE)).thenReturn(Optional.of(entity));
        when(planCourseJpaRepository.findByPlanId(planId))
            .thenReturn(List.of(new PlanCourseJpaEntity(planId, "course-1")));

        Optional<Plan> plan = adapter.findActiveById(id);

        assertThat(plan).isPresent();
        assertThat(plan.get().getId()).isEqualTo(id);
    }

    @Test
    void findActiveById_empty_when_absent() {
        UUID planId = UUID.randomUUID();
        when(planJpaRepository.findByIdAndStatus(planId, PlanStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThat(adapter.findActiveById(PlanId.of(planId))).isEmpty();
    }

    /** Escenario 2b: activation reads the plan whatever its status, so a deactivated plan still snapshots. */
    @Test
    void findById_maps_an_inactive_plan() {
        UUID planId = UUID.randomUUID();
        Instant now = Instant.now();
        PlanJpaEntity entity = new PlanJpaEntity(
            planId, "Plan", "Desc", BigDecimal.ONE, "ARS",
            30, false, PlanStatus.INACTIVE, "T", "C", now, now
        );
        when(planJpaRepository.findById(planId)).thenReturn(Optional.of(entity));
        when(planCourseJpaRepository.findByPlanId(planId))
            .thenReturn(List.of(new PlanCourseJpaEntity(planId, "course-1")));
        when(planPaymentMethodJpaRepository.findByPlanId(planId))
            .thenReturn(List.of(new PlanPaymentMethodJpaEntity(planId, "MERCADO_PAGO")));

        Optional<Plan> plan = adapter.findById(PlanId.of(planId));

        assertThat(plan).isPresent();
        assertThat(plan.get().getStatus()).isEqualTo(PlanStatus.INACTIVE);
        assertThat(plan.get().courseIds()).containsExactly("course-1");
    }

    @Test
    void findById_empty_when_absent() {
        UUID planId = UUID.randomUUID();
        when(planJpaRepository.findById(planId)).thenReturn(Optional.empty());

        assertThat(adapter.findById(PlanId.of(planId))).isEmpty();
    }

    @Test
    void findAllActiveOrderByPriceAsc_joins_payment_methods_too() {
        UUID planId = UUID.randomUUID();
        when(planJpaRepository.findAllByStatusOrderByPriceAsc(PlanStatus.ACTIVE)).thenReturn(List.of(plan(planId)));
        when(planCourseJpaRepository.findByPlanIdIn(List.of(planId))).thenReturn(List.of());
        when(planPaymentMethodJpaRepository.findByPlanIdIn(List.of(planId)))
            .thenReturn(List.of(new PlanPaymentMethodJpaEntity(planId, "BANK_TRANSFER")));

        List<Plan> plans = adapter.findAllActiveOrderByPriceAsc();

        assertThat(plans.get(0).getPaymentMethods()).containsExactly(PaymentMethod.BANK_TRANSFER);
    }
}
