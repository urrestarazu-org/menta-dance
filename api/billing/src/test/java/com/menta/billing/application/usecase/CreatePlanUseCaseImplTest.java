package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.CreatePlanCommand;
import com.menta.billing.application.dto.PlanCourseResult;
import com.menta.billing.application.dto.PlanDetailResult;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.application.port.out.PlanRepository;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.PaymentMethod;
import com.menta.billing.domain.model.Plan;
import com.menta.billing.domain.model.PlanCourse;
import com.menta.billing.domain.model.PlanStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class CreatePlanUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");
    private static final UUID ADMIN_ID = UUID.randomUUID();

    private PlanRepository planRepository;
    private CourseCatalogPort courseCatalogPort;
    private CreatePlanUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        planRepository = mock(PlanRepository.class);
        courseCatalogPort = mock(CourseCatalogPort.class);
        Clock clock = mock(Clock.class);
        when(clock.now()).thenReturn(NOW);
        useCase = new CreatePlanUseCaseImpl(planRepository, courseCatalogPort, clock);
    }

    private static CreatePlanCommand command(
        BigDecimal price, String currency, int durationDays, Set<PaymentMethod> methods, List<String> courseIds
    ) {
        return new CreatePlanCommand(
            "Plan Mensual", "Acceso mensual", price, currency, durationDays, true, PlanStatus.ACTIVE,
            "Términos", "Política de cancelación", methods, courseIds, ADMIN_ID
        );
    }

    private static CreatePlanCommand validCommand(List<String> courseIds) {
        return command(
            new BigDecimal("15000.00"), "ARS", 30, Set.of(PaymentMethod.MERCADO_PAGO, PaymentMethod.BANK_TRANSFER),
            courseIds
        );
    }

    private Plan savedPlan() {
        ArgumentCaptor<Plan> plan = ArgumentCaptor.forClass(Plan.class);
        verify(planRepository).save(plan.capture(), any(Instant.class));
        return plan.getValue();
    }

    @Test
    void persists_the_plan_with_every_field_of_the_command_stamped_with_the_clock() {
        useCase.create(validCommand(List.of("course-1", "course-2")));

        ArgumentCaptor<Plan> plan = ArgumentCaptor.forClass(Plan.class);
        verify(planRepository).save(plan.capture(), eq(NOW));
        Plan saved = plan.getValue();
        assertThat(saved.getName()).isEqualTo("Plan Mensual");
        assertThat(saved.getDescription()).isEqualTo("Acceso mensual");
        assertThat(saved.getPrice()).isEqualTo(Money.of(new BigDecimal("15000.00"), "ARS"));
        assertThat(saved.getDurationDays()).isEqualTo(30);
        assertThat(saved.isFeatured()).isTrue();
        assertThat(saved.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(saved.getTermsAndConditions()).isEqualTo("Términos");
        assertThat(saved.getCancellationPolicy()).isEqualTo("Política de cancelación");
        assertThat(saved.getPaymentMethods())
            .containsExactlyInAnyOrder(PaymentMethod.MERCADO_PAGO, PaymentMethod.BANK_TRANSFER);
        assertThat(saved.getCourses()).containsExactly(PlanCourse.of("course-1"), PlanCourse.of("course-2"));
    }

    @Test
    void generates_a_fresh_server_side_id_for_every_plan_and_returns_it() {
        PlanDetailResult first = useCase.create(validCommand(List.of()));
        PlanDetailResult second = useCase.create(validCommand(List.of()));

        assertThat(UUID.fromString(first.id())).isNotNull();
        assertThat(first.id()).isNotEqualTo(second.id());
    }

    @Test
    void returns_the_public_detail_of_the_created_plan_with_resolved_course_names() {
        when(courseCatalogPort.courseNames(anyCollection())).thenReturn(Map.of("course-1", "Tango Básico"));

        PlanDetailResult result = useCase.create(validCommand(List.of("course-1", "course-2")));

        assertThat(result.id()).isEqualTo(savedPlan().getId().toString());
        assertThat(result.name()).isEqualTo("Plan Mensual");
        assertThat(result.description()).isEqualTo("Acceso mensual");
        assertThat(result.price()).isEqualByComparingTo("15000.00");
        assertThat(result.currency()).isEqualTo("ARS");
        assertThat(result.durationDays()).isEqualTo(30);
        assertThat(result.featured()).isTrue();
        assertThat(result.termsAndConditions()).isEqualTo("Términos");
        assertThat(result.cancellationPolicy()).isEqualTo("Política de cancelación");
        assertThat(result.courses()).containsExactly(
            new PlanCourseResult("course-1", "Tango Básico"), new PlanCourseResult("course-2", null)
        );
    }

    @Test
    void a_plan_without_courses_is_created_without_asking_the_course_catalog() {
        PlanDetailResult result = useCase.create(validCommand(List.of()));

        assertThat(result.courses()).isEmpty();
        assertThat(savedPlan().getCourses()).isEmpty();
        verifyNoInteractions(courseCatalogPort);
    }

    @Test
    void an_inactive_plan_is_persisted_as_inactive() {
        useCase.create(new CreatePlanCommand(
            "Plan Anual", "Acceso anual", BigDecimal.TEN, "ARS", 365, false, PlanStatus.INACTIVE,
            "Términos", "Política", Set.of(PaymentMethod.BANK_TRANSFER), List.of(), ADMIN_ID
        ));

        assertThat(savedPlan().getStatus()).isEqualTo(PlanStatus.INACTIVE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1.00"})
    void a_price_that_is_not_positive_is_rejected_without_persisting(String price) {
        CreatePlanCommand command = command(
            new BigDecimal(price), "ARS", 30, Set.of(PaymentMethod.MERCADO_PAGO), List.of()
        );

        assertThatThrownBy(() -> useCase.create(command)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(planRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ars", "PESOS", ""})
    void a_currency_that_is_not_an_iso_4217_code_is_rejected_without_persisting(String currency) {
        CreatePlanCommand command = command(
            BigDecimal.TEN, currency, 30, Set.of(PaymentMethod.MERCADO_PAGO), List.of()
        );

        assertThatThrownBy(() -> useCase.create(command)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(planRepository);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -30})
    void a_duration_that_is_not_positive_is_rejected_without_persisting(int durationDays) {
        CreatePlanCommand command = command(
            BigDecimal.TEN, "ARS", durationDays, Set.of(PaymentMethod.MERCADO_PAGO), List.of()
        );

        assertThatThrownBy(() -> useCase.create(command)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(planRepository);
    }

    @Test
    void a_plan_without_payment_methods_is_rejected_without_persisting() {
        CreatePlanCommand command = command(BigDecimal.TEN, "ARS", 30, Set.of(), List.of());

        assertThatThrownBy(() -> useCase.create(command)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(planRepository);
    }

    @Test
    void a_blank_course_id_is_rejected_without_persisting() {
        CreatePlanCommand command = command(
            BigDecimal.TEN, "ARS", 30, Set.of(PaymentMethod.MERCADO_PAGO), List.of("course-1", "  ")
        );

        assertThatThrownBy(() -> useCase.create(command)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(planRepository);
    }
}
