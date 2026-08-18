package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.PlanSummaryResult;
import com.menta.billing.application.dto.RateLimitDecision;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.application.port.out.PlanRepository;
import com.menta.billing.domain.exception.PlanRateLimitedException;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Plan;
import com.menta.billing.domain.model.PlanCourse;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.domain.model.PlanStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListPlansUseCaseImplTest {

    private static final String CLIENT_FINGERPRINT = "a".repeat(64);

    private PlanRepository planRepository;
    private CourseCatalogPort courseCatalogPort;
    private BillingPlansRateLimitPort rateLimitPort;
    private ListPlansUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        planRepository = mock(PlanRepository.class);
        courseCatalogPort = mock(CourseCatalogPort.class);
        rateLimitPort = mock(BillingPlansRateLimitPort.class);
        useCase = new ListPlansUseCaseImpl(planRepository, courseCatalogPort, rateLimitPort);
    }

    private void allowRateLimit() {
        when(rateLimitPort.consume(any())).thenReturn(RateLimitDecision.allowed());
    }

    private static Plan plan(String name, boolean featured, List<PlanCourse> courses) {
        return new Plan(
            PlanId.generate(), name, "desc", Money.of(BigDecimal.TEN, "ARS"), 30,
            featured, PlanStatus.ACTIVE, "terms", "cancellation", courses
        );
    }

    @Test
    void maps_every_active_plan_returned_by_the_repository() {
        allowRateLimit();
        Plan plan = plan("Plan Mensual", true, List.of());
        when(planRepository.findAllActiveOrderByPriceAsc()).thenReturn(List.of(plan));

        List<PlanSummaryResult> result = useCase.listActivePlans(CLIENT_FINGERPRINT);

        assertThat(result).hasSize(1);
        PlanSummaryResult summary = result.get(0);
        assertThat(summary.id()).isEqualTo(plan.getId().toString());
        assertThat(summary.name()).isEqualTo("Plan Mensual");
        assertThat(summary.featured()).isTrue();
    }

    @Test
    void returns_an_empty_list_when_the_repository_has_no_active_plans() {
        allowRateLimit();
        when(planRepository.findAllActiveOrderByPriceAsc()).thenReturn(List.of());

        assertThat(useCase.listActivePlans(CLIENT_FINGERPRINT)).isEmpty();
    }

    @Test
    void enriches_each_course_with_its_resolved_name() {
        allowRateLimit();
        Plan plan = plan("Plan", false, List.of(PlanCourse.of("course-1")));
        when(planRepository.findAllActiveOrderByPriceAsc()).thenReturn(List.of(plan));
        when(courseCatalogPort.courseName("course-1")).thenReturn(Optional.of("Tango Basico"));

        List<PlanSummaryResult> result = useCase.listActivePlans(CLIENT_FINGERPRINT);

        assertThat(result.get(0).courses()).singleElement()
            .satisfies(course -> {
                assertThat(course.courseId()).isEqualTo("course-1");
                assertThat(course.courseName()).isEqualTo("Tango Basico");
            });
    }

    @Test
    void a_course_the_catalog_cannot_resolve_gets_a_null_name_not_an_exception() {
        allowRateLimit();
        Plan plan = plan("Plan", false, List.of(PlanCourse.of("course-1")));
        when(planRepository.findAllActiveOrderByPriceAsc()).thenReturn(List.of(plan));
        when(courseCatalogPort.courseName(any())).thenReturn(Optional.empty());

        List<PlanSummaryResult> result = useCase.listActivePlans(CLIENT_FINGERPRINT);

        assertThat(result.get(0).courses().get(0).courseName()).isNull();
    }

    @Test
    void a_catalog_port_that_throws_degrades_to_a_null_name_instead_of_failing_the_whole_list() {
        // The real scenario today: NotImplementedCourseCatalogPort throws
        // because #40/#46 do not exist yet. That must never 500 the whole
        // plans listing over a missing display name.
        allowRateLimit();
        Plan plan = plan("Plan", false, List.of(PlanCourse.of("course-1")));
        when(planRepository.findAllActiveOrderByPriceAsc()).thenReturn(List.of(plan));
        when(courseCatalogPort.courseName(eq("course-1")))
            .thenThrow(new UnsupportedOperationException("not implemented yet"));

        List<PlanSummaryResult> result = useCase.listActivePlans(CLIENT_FINGERPRINT);

        assertThat(result.get(0).courses().get(0).courseName()).isNull();
    }

    @Test
    void an_exhausted_budget_rejects_before_touching_the_repository() {
        when(rateLimitPort.consume(CLIENT_FINGERPRINT))
            .thenReturn(RateLimitDecision.limited(Duration.ofSeconds(12)));

        assertThatThrownBy(() -> useCase.listActivePlans(CLIENT_FINGERPRINT))
            .isInstanceOf(PlanRateLimitedException.class)
            .extracting(ex -> ((PlanRateLimitedException) ex).getRetryAfter())
            .isEqualTo(Duration.ofSeconds(12));
        verifyNoInteractions(planRepository);
    }
}
